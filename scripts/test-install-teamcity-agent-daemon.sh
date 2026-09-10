#!/bin/bash
# Renders the LaunchDaemon plist and wrapper against a fake agent installation, validates
# them, and exercises the wrapper's SIGTERM path against a stub agent.
# Runs without root; nothing is installed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALLER="${SCRIPT_DIR}/install-teamcity-agent-daemon.sh"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

AGENT_DIR="${WORK_DIR}/BuildAgent"
PLIST_DIR="${WORK_DIR}/LaunchDaemons"
AGENT_USER="$(id -un)"
AGENT_GROUP="$(id -gn)"
mkdir -p "${AGENT_DIR}/bin" "${AGENT_DIR}/conf" "$PLIST_DIR"
printf '#!/bin/sh\nexit 0\n' > "${AGENT_DIR}/bin/agent.sh"
chmod +x "${AGENT_DIR}/bin/agent.sh"
printf 'serverUrl=https://teamcity.example.com\n' > "${AGENT_DIR}/conf/buildAgent.properties"
cat > "${AGENT_DIR}/bin/jetbrains.teamcity.BuildAgentUpgrade.plist.dist" <<'UPGRADE'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>jetbrains.teamcity.BuildAgentUpgrade</string>
</dict>
</plist>
UPGRADE

failures=0
check() {
    if [ "$2" = "true" ]; then
        echo "ok   - $1"
    else
        echo "FAIL - $1"
        failures=$((failures + 1))
    fi
}

TEAMCITY_DAEMON_DRY_RUN_DIR="$PLIST_DIR" "$INSTALLER" \
    --agent-dir "$AGENT_DIR" --user "$AGENT_USER" --group "$AGENT_GROUP" > "${WORK_DIR}/out.log"

PLIST="${PLIST_DIR}/jetbrains.teamcity.BuildAgent.plist"
WRAPPER="${PLIST_DIR}/teamcity-agent-service.sh"
UPGRADE_PLIST="${AGENT_DIR}/bin/jetbrains.teamcity.BuildAgentUpgrade.plist.dist"

check "plist is created" "$([ -f "$PLIST" ] && echo true || echo false)"
check "plist is valid XML property list" "$(plutil -lint "$PLIST" >/dev/null 2>&1 && echo true || echo false)"
check "upgrade template is valid XML property list" \
    "$(plutil -lint "$UPGRADE_PLIST" >/dev/null 2>&1 && echo true || echo false)"
check "runs as the requested user" \
    "$([ "$(plutil -extract UserName raw -o - "$PLIST")" = "$AGENT_USER" ] && echo true || echo false)"
check "starts at load" \
    "$([ "$(plutil -extract RunAtLoad raw -o - "$PLIST")" = "true" ] && echo true || echo false)"
check "creates a security session for keychain access" \
    "$([ "$(plutil -extract SessionCreate raw -o - "$PLIST")" = "true" ] && echo true || echo false)"
check "sets HOME so agent toolchain lookups resolve" \
    "$(plutil -extract EnvironmentVariables.HOME raw -o - "$PLIST" >/dev/null 2>&1 && echo true || echo false)"
check "launches the wrapper, not agent.sh directly" \
    "$(plutil -extract ProgramArguments.2 raw -o - "$PLIST" | grep -q "^${WRAPPER}$" \
        && echo true || echo false)"
check "gives the agent time to unregister before SIGKILL" \
    "$([ "$(plutil -extract ExitTimeOut raw -o - "$PLIST")" -ge 30 ] && echo true || echo false)"
check "wrapper is created and executable" \
    "$([ -x "$WRAPPER" ] && echo true || echo false)"
check "wrapper is syntactically valid bash" \
    "$(bash -n "$WRAPPER" >/dev/null 2>&1 && echo true || echo false)"
check "wrapper points at the given agent dir" \
    "$(grep -q "^AGENT=\"${AGENT_DIR}/bin/agent.sh\"$" "$WRAPPER" && echo true || echo false)"
check "wrapper traps TERM" \
    "$(grep -q '^trap stop_agent TERM INT$' "$WRAPPER" && echo true || echo false)"
check "respawns only on a failed exit, so a clean stop stays stopped" \
    "$([ "$(plutil -extract KeepAlive.SuccessfulExit raw -o - "$PLIST")" = "false" ] && echo true || echo false)"
check "throttles respawns" \
    "$([ "$(plutil -extract ThrottleInterval raw -o - "$PLIST")" -gt 0 ] && echo true || echo false)"
check "upgrade template gets UserName so upgrades do not run as root" \
    "$([ "$(plutil -extract UserName raw -o - "$UPGRADE_PLIST")" = "$AGENT_USER" ] && echo true || echo false)"

# Re-running must not inject UserName into the upgrade template twice.
TEAMCITY_DAEMON_DRY_RUN_DIR="$PLIST_DIR" "$INSTALLER" \
    --agent-dir "$AGENT_DIR" --user "$AGENT_USER" --group "$AGENT_GROUP" > /dev/null
check "re-running leaves a valid upgrade template" \
    "$(plutil -lint "$UPGRADE_PLIST" >/dev/null 2>&1 && echo true || echo false)"
check "re-running does not duplicate UserName" \
    "$([ "$(grep -c '<key>UserName</key>' "$UPGRADE_PLIST")" -eq 1 ] && echo true || echo false)"

# A missing serverUrl must warn, since the Orka plugin never supplies one.
printf 'name=whatever\n' > "${AGENT_DIR}/conf/buildAgent.properties"
TEAMCITY_DAEMON_DRY_RUN_DIR="$PLIST_DIR" "$INSTALLER" \
    --agent-dir "$AGENT_DIR" --user "$AGENT_USER" > /dev/null 2> "${WORK_DIR}/warn.log"
check "warns when serverUrl is missing" \
    "$(grep -q 'no serverUrl' "${WORK_DIR}/warn.log" && echo true || echo false)"

# A bad agent dir must fail rather than render a broken plist.
set +e
TEAMCITY_DAEMON_DRY_RUN_DIR="$PLIST_DIR" "$INSTALLER" \
    --agent-dir "${WORK_DIR}/nope" --user "$AGENT_USER" > /dev/null 2>&1
missing_agent_status=$?
set -e
check "fails when the agent dir has no agent" "$([ "$missing_agent_status" -ne 0 ] && echo true || echo false)"

# The point of the wrapper: SIGTERM must reach the agent as a stop, not a kill.
STOP_WORK="${WORK_DIR}/stop-test"
STOP_AGENT_DIR="${STOP_WORK}/BuildAgent"
CALL_LOG="${STOP_WORK}/calls.log"
mkdir -p "${STOP_AGENT_DIR}/bin" "${STOP_AGENT_DIR}/conf" "${STOP_WORK}/out"
cat > "${STOP_AGENT_DIR}/bin/agent.sh" <<STUB
#!/bin/sh
echo "\$@" >> "${CALL_LOG}"
# Fail the first start the way a not-yet-ready dependency would, then succeed.
if [ "\$1" = "start" ] && [ ! -f "${STOP_WORK}/started-once" ]; then
    touch "${STOP_WORK}/started-once"
    exit 1
fi
exit 0
STUB
chmod +x "${STOP_AGENT_DIR}/bin/agent.sh"
printf 'serverUrl=https://teamcity.example.com\n' > "${STOP_AGENT_DIR}/conf/buildAgent.properties"
TEAMCITY_DAEMON_DRY_RUN_DIR="${STOP_WORK}/out" "$INSTALLER" \
    --agent-dir "$STOP_AGENT_DIR" --user "$AGENT_USER" --group "$AGENT_GROUP" > /dev/null

bash "${STOP_WORK}/out/teamcity-agent-service.sh" &
WRAPPER_PID=$!

started="false"
for _ in $(seq 1 200); do
    if [ "$(grep -c '^start$' "$CALL_LOG" 2>/dev/null || echo 0)" -ge 2 ]; then
        started="true"
        break
    fi
    sleep 0.1
done
check "wrapper survives a failed start and retries" "$started"
check "wrapper retried rather than giving up" \
    "$([ "$(grep -c '^start$' "$CALL_LOG")" -ge 2 ] && echo true || echo false)"

kill -TERM "$WRAPPER_PID" 2>/dev/null || true
exited="false"
for _ in $(seq 1 50); do
    if ! kill -0 "$WRAPPER_PID" 2>/dev/null; then
        exited="true"
        break
    fi
    sleep 0.1
done
wait "$WRAPPER_PID" 2>/dev/null || true
check "wrapper exits on SIGTERM instead of being killed" "$exited"
check "wrapper stops the agent on SIGTERM" \
    "$(grep -q '^stop force$' "$CALL_LOG" && echo true || echo false)"

# Uninstall must take the wrapper with it, or a stale wrapper survives in the image.
TEAMCITY_DAEMON_DRY_RUN_DIR="$PLIST_DIR" "$INSTALLER" --uninstall > /dev/null
check "uninstall removes the plist" "$([ ! -f "$PLIST" ] && echo true || echo false)"
check "uninstall removes the wrapper" "$([ ! -f "$WRAPPER" ] && echo true || echo false)"

echo
if [ "$failures" -eq 0 ]; then
    echo "All checks passed."
else
    echo "${failures} check(s) failed."
    exit 1
fi
