#!/bin/bash
# Installs a LaunchDaemon that starts the TeamCity build agent on VM boot, running as an
# unprivileged user, without requiring anyone to log in. The daemon runs a small wrapper so
# that launchd can stop the agent gracefully on shutdown.
#
# Run this INSIDE the Orka VM you are about to save as an image, as root (sudo), after the
# TeamCity agent is installed and has connected to the server at least once.

set -euo pipefail

LABEL="jetbrains.teamcity.BuildAgent"
# Set to a directory to render the plist and wrapper there and skip root, chown and
# launchctl. Test hook only.
DRY_RUN_DIR="${TEAMCITY_DAEMON_DRY_RUN_DIR:-}"
PLIST_DIR="${DRY_RUN_DIR:-/Library/LaunchDaemons}"
PLIST="${PLIST_DIR}/${LABEL}.plist"
WRAPPER_DIR="${DRY_RUN_DIR:-/usr/local/libexec}"
WRAPPER="${WRAPPER_DIR}/teamcity-agent-service.sh"
# launchd SIGKILLs the job this many seconds after SIGTERM, so the agent has this long to
# unregister from the server.
EXIT_TIMEOUT=60
# Respawn only on a failed exit, so the SIGTERM stop path (exit 0) does not get restarted while
# the VM is shutting down.
THROTTLE_INTERVAL=30
UPGRADE_LABEL="jetbrains.teamcity.BuildAgentUpgrade"
UPGRADE_PLIST_TEMPLATE_SUFFIX="bin/${UPGRADE_LABEL}.plist.dist"

usage() {
    cat <<USAGE
Usage: sudo $0 --agent-dir <path> --user <username> [--group <groupname>] [--uninstall]

  --agent-dir  TeamCity agent home, the directory containing bin/ and conf/
               (for example /Users/admin/BuildAgent)
  --user       User the agent process runs as (for example admin)
  --group      Primary group for that user (default: staff)
  --uninstall  Remove the LaunchDaemon and wrapper instead of installing them

The agent must already be installed and configured: conf/buildAgent.properties needs a
valid serverUrl, since the Orka plugin does not supply one.
USAGE
}

AGENT_DIR=""
AGENT_USER=""
AGENT_GROUP="staff"
UNINSTALL="false"

while [ $# -gt 0 ]; do
    case "$1" in
        --agent-dir) AGENT_DIR="${2:-}"; shift 2 ;;
        --user) AGENT_USER="${2:-}"; shift 2 ;;
        --group) AGENT_GROUP="${2:-}"; shift 2 ;;
        --uninstall) UNINSTALL="true"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done

if [ -z "$DRY_RUN_DIR" ] && [ "$(id -u)" -ne 0 ]; then
    echo "This script must be run as root (use sudo)." >&2
    exit 1
fi

if [ "$UNINSTALL" = "true" ]; then
    if [ -f "$PLIST" ]; then
        if [ -z "$DRY_RUN_DIR" ]; then
            launchctl bootout "system/${LABEL}" 2>/dev/null || true
        fi
        rm -f "$PLIST"
        echo "Removed ${PLIST}"
    else
        echo "Nothing to remove: ${PLIST} does not exist"
    fi
    if [ -f "$WRAPPER" ]; then
        rm -f "$WRAPPER"
        echo "Removed ${WRAPPER}"
    fi
    exit 0
fi

if [ -z "$AGENT_DIR" ] || [ -z "$AGENT_USER" ]; then
    usage >&2
    exit 2
fi

if [ ! -x "${AGENT_DIR}/bin/agent.sh" ]; then
    echo "No agent found: ${AGENT_DIR}/bin/agent.sh is missing or not executable." >&2
    exit 1
fi

if ! id "$AGENT_USER" >/dev/null 2>&1; then
    echo "No such user: ${AGENT_USER}" >&2
    exit 1
fi

PROPERTIES="${AGENT_DIR}/conf/buildAgent.properties"
if [ ! -f "$PROPERTIES" ]; then
    echo "No ${PROPERTIES}. Install and configure the agent before running this script." >&2
    exit 1
fi

if ! grep -Eq '^[[:space:]]*serverUrl[[:space:]]*=[[:space:]]*[^[:space:]]' "$PROPERTIES"; then
    echo "WARNING: no serverUrl in ${PROPERTIES}. The Orka plugin does not supply one, so the" >&2
    echo "         agent will not find the server. Set it before saving the image." >&2
fi

AGENT_HOME="$(eval echo "~${AGENT_USER}")"
LOG_DIR="${AGENT_DIR}/logs"
mkdir -p "$LOG_DIR"
if [ -z "$DRY_RUN_DIR" ]; then
    chown -R "${AGENT_USER}:${AGENT_GROUP}" "$AGENT_DIR"
    mkdir -p "$WRAPPER_DIR"
fi

cat > "$WRAPPER" <<WRAPPER_CONTENT
#!/bin/bash
# agent.sh start forks and exits, so launchd would treat the job as finished and would have
# nothing left to signal at shutdown. Staying in the foreground keeps the daemon alive and
# gives launchd something to send SIGTERM to, which is what lets the agent unregister.
set -eu

AGENT="${AGENT_DIR}/bin/agent.sh"
PID_FILE="${AGENT_DIR}/logs/buildAgent.pid"

if [ -z "\${JAVA_HOME:-}" ]; then
    JAVA_HOME="\$(/usr/libexec/java_home 2>/dev/null || true)"
    if [ -n "\$JAVA_HOME" ]; then
        export JAVA_HOME
    fi
fi

stop_agent() {
    trap '' TERM INT
    # 'stop force' rather than 'stop': the VM is being destroyed anyway, and waiting for the
    # running build to finish risks hitting launchd's ExitTimeOut before the agent has
    # unregistered, which is the one thing this wrapper exists to do.
    "\$AGENT" stop force
    exit 0
}
trap stop_agent TERM INT

agent_running() {
    [ -f "\$PID_FILE" ] && kill -0 "\$(cat "\$PID_FILE" 2>/dev/null)" 2>/dev/null
}

# A start can fail because a dependency is not up yet on first boot, so keep trying instead of
# letting set -e kill the daemon for good.
if agent_running; then
    echo "agent already running, not starting it again"
else
    until "\$AGENT" start; do
        echo "agent.sh start failed, retrying in 10s" >&2
        sleep 10
    done
fi

# Sleep in the background and wait on it: bash defers traps until the foreground builtin
# returns, so a plain 'sleep' would delay shutdown by up to the sleep interval.
while true; do
    sleep 5 &
    wait \$! || true
done
WRAPPER_CONTENT

chmod 755 "$WRAPPER"
if [ -z "$DRY_RUN_DIR" ]; then
    chown root:wheel "$WRAPPER"
fi

cat > "$PLIST" <<PLIST_CONTENT
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${LABEL}</string>
    <key>UserName</key>
    <string>${AGENT_USER}</string>
    <key>GroupName</key>
    <string>${AGENT_GROUP}</string>
    <key>InitGroups</key>
    <true/>
    <key>SessionCreate</key>
    <true/>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <dict>
        <key>SuccessfulExit</key>
        <false/>
    </dict>
    <key>ThrottleInterval</key>
    <integer>${THROTTLE_INTERVAL}</integer>
    <key>ExitTimeOut</key>
    <integer>${EXIT_TIMEOUT}</integer>
    <key>WorkingDirectory</key>
    <string>${AGENT_DIR}</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>HOME</key>
        <string>${AGENT_HOME}</string>
        <key>USER</key>
        <string>${AGENT_USER}</string>
    </dict>
    <key>ProgramArguments</key>
    <array>
        <string>/bin/bash</string>
        <string>--login</string>
        <string>${WRAPPER}</string>
    </array>
    <key>StandardOutPath</key>
    <string>${LOG_DIR}/launchd.out.log</string>
    <key>StandardErrorPath</key>
    <string>${LOG_DIR}/launchd.err.log</string>
</dict>
</plist>
PLIST_CONTENT

if [ -z "$DRY_RUN_DIR" ]; then
    chown root:wheel "$PLIST"
fi
chmod 644 "$PLIST"

# The agent restarts itself to upgrade. Without UserName here too, launchd runs the upgrade
# as root and it writes root-owned files into the agent directory, after which the agent can
# no longer restart itself.
UPGRADE_TEMPLATE="${AGENT_DIR}/${UPGRADE_PLIST_TEMPLATE_SUFFIX}"
if [ -f "$UPGRADE_TEMPLATE" ] && ! grep -q "<key>UserName</key>" "$UPGRADE_TEMPLATE"; then
    python3 - "$UPGRADE_TEMPLATE" "$AGENT_USER" "$AGENT_GROUP" <<'PYTHON'
import sys

path, user, group = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path) as handle:
    contents = handle.read()

marker = "<dict>"
index = contents.index(marker) + len(marker)
injected = "\n    <key>UserName</key>\n    <string>%s</string>\n    <key>GroupName</key>\n    <string>%s</string>" % (
    user, group)
with open(path, "w") as handle:
    handle.write(contents[:index] + injected + contents[index:])
PYTHON
    echo "Added UserName to ${UPGRADE_TEMPLATE}"
fi

if [ -n "$DRY_RUN_DIR" ]; then
    echo "Dry run: rendered ${PLIST} and ${WRAPPER}, skipped launchctl."
    exit 0
fi

launchctl bootout "system/${LABEL}" 2>/dev/null || true
launchctl bootstrap system "$PLIST"

echo
echo "Installed ${PLIST}"
echo "Installed ${WRAPPER}"
echo "The agent will start as '${AGENT_USER}' on every boot and unregister on shutdown."
echo
echo "Verify with:  sudo launchctl print system/${LABEL} | head -20"
echo "Agent log:    ${LOG_DIR}/teamcity-agent.log"
echo "Wrapper log:  ${LOG_DIR}/launchd.out.log"
echo
echo "Test the stop path without rebooting:"
echo "  sudo launchctl bootout system/${LABEL}"
echo "  grep -i unregister ${LOG_DIR}/teamcity-agent.log"
echo
echo "Next: stop the agent, clear ${AGENT_DIR}/logs and ${AGENT_DIR}/temp, remove 'name' from"
echo "${PROPERTIES}, then save the VM as an Orka image."
echo
echo "NOTE: a LaunchDaemon has no GUI session, so iOS Simulator, UI tests and the login"
echo "      keychain will not work. Those need auto-login plus a LaunchAgent instead."
echo "NOTE: Orka's VM delete is a hard power-off, so launchd never runs the stop path on"
echo "      delete. This only helps on 'launchctl bootout' and real OS shutdowns."
