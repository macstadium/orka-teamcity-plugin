#!/bin/bash
# Installs a LaunchDaemon that starts the TeamCity build agent on VM boot, running as an
# unprivileged user, without requiring anyone to log in.
#
# Run this INSIDE the Orka VM you are about to save as an image, as root (sudo), after the
# TeamCity agent is installed and has connected to the server at least once.

set -euo pipefail

LABEL="jetbrains.teamcity.BuildAgent"
# Set to a directory to render the plist there and skip root, chown and launchctl. Test hook only.
DRY_RUN_DIR="${TEAMCITY_DAEMON_DRY_RUN_DIR:-}"
PLIST_DIR="${DRY_RUN_DIR:-/Library/LaunchDaemons}"
PLIST="${PLIST_DIR}/${LABEL}.plist"
UPGRADE_LABEL="jetbrains.teamcity.BuildAgentUpgrade"
UPGRADE_PLIST_TEMPLATE_SUFFIX="bin/${UPGRADE_LABEL}.plist.dist"

usage() {
    cat <<USAGE
Usage: sudo $0 --agent-dir <path> --user <username> [--group <groupname>] [--uninstall]

  --agent-dir  TeamCity agent home, the directory containing bin/ and conf/
               (for example /Users/admin/BuildAgent)
  --user       User the agent process runs as (for example admin)
  --group      Primary group for that user (default: staff)
  --uninstall  Remove the LaunchDaemon instead of installing it

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
        launchctl bootout "system/${LABEL}" 2>/dev/null || true
        rm -f "$PLIST"
        echo "Removed ${PLIST}"
    else
        echo "Nothing to remove: ${PLIST} does not exist"
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
        <string>-c</string>
        <string>${AGENT_DIR}/bin/agent.sh start</string>
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
    echo "Dry run: rendered ${PLIST}, skipped launchctl."
    exit 0
fi

launchctl bootout "system/${LABEL}" 2>/dev/null || true
launchctl bootstrap system "$PLIST"

echo
echo "Installed ${PLIST}"
echo "The agent will start as '${AGENT_USER}' on every boot."
echo
echo "Verify with:  sudo launchctl print system/${LABEL} | head -20"
echo "Agent log:    ${LOG_DIR}/teamcity-agent.log"
echo
echo "Next: stop the agent, clear ${AGENT_DIR}/logs and ${AGENT_DIR}/temp, remove 'name' from"
echo "${PROPERTIES}, then save the VM as an Orka image."
echo
echo "NOTE: a LaunchDaemon has no GUI session, so iOS Simulator, UI tests and the login"
echo "      keychain will not work. Those need auto-login plus a LaunchAgent instead."
