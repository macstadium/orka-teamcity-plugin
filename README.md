# Orka by MacStadium TeamCity Plugin

This readme provides information about how to build, package, or run the plugin locally. For usage information, see the TeamCity plugin [tutorial][tutorial].

The plugin provides TeamCity cloud integration with Orka by MacStadium. This allows users to configure TeamCity, so it can provision and tear down agents running in Orka on demand.  

The plugin uses [gradle plugin][gradle-plugin] to build and package the plugin. For more information, see the gradle plugin [page][gradle-plugin].

## Agent setup modes

The cloud profile picks how the TeamCity agent inside the VM is started and how it learns
which cloud instance it belongs to. Both modes produce the same result: an agent that
registers with `cloud.orka.instance.id` and `cloud.orka.image.id` set, so TeamCity can match
it to the instance it started.

| Mode | Starts the agent | Carries the identity | Needs SSH | Needs orka-vm-tools | Needs image prep | Stops the agent |
|---|---|---|---|---|---|---|
| SSH (default) | server, over SSH | file SCP'd to `/tmp` | yes | no | agent installed, `serverUrl` set, SSH on | yes, `agent.sh stop` |
| Daemon | LaunchDaemon in the image | Orka metadata service | no | yes | plus `install-teamcity-agent-daemon.sh` | no |

`serverUrl` must be set in `conf/buildAgent.properties` in both modes.

### SSH

The original behaviour, and the default when a cloud profile does not set an agent setup
mode. The server waits for the VM's SSH port, uploads a metadata file and runs
`agent.sh start`. On termination it runs `agent.sh stop` before deleting the VM.

Unchanged by the daemon work, including its requirements: an SSH-mode agent finds its uploaded
file and never opens a connection to the metadata service, so orka-vm-tools stays optional here.

One thing did change. An instance whose agent could not be reached over SSH at termination now
reports `STOPPING` before the VM is deleted, where it used to go from `SCHEDULED_TO_STOP` straight
to `STOPPED`: the status is now set by the client rather than by the SSH stop itself, so it no
longer depends on the connection succeeding. The VM is deleted either way.

### Daemon

Opt-in per cloud profile. The VM image starts the agent itself, and the agent-side plugin
reads its identity from the Orka metadata service (`orka_vm_name`, plus `teamcity_image_id`
and `teamcity_starting_instance_id` passed as custom metadata on deploy). The server only
deploys and deletes the VM.

Because nothing connects into the VM, this mode also works with FileVault turned on in the
image. The disk still has to be unlocked after the VM boots — by someone over VNC or by
something you set up — since a LaunchDaemon only runs once the volume is unlocked and macOS has
booted. The plugin does not unlock it.

**orka-vm-tools is required in the image.** It serves the metadata service on the link-local
address, which is the only thing carrying the agent's identity in this mode; without it the agent
boots, finds nothing, and leaves itself unconfigured. It ships in the default images MacStadium
publishes, so this only needs doing for an image built from scratch:

```bash
brew install --cask orka-vm-tools
```

Confirm it is serving before saving the image, from inside a deployed VM:

```bash
curl -s http://169.254.169.254/metadata/orka_vm_name
```

The agent tells the two modes apart by whether `orka_metadata_file` is on disk. SSH mode uploads
that file and only then runs `agent.sh start`, so an agent that finds one was started that way,
and an agent that finds none was not. The one way the absent case could lie is an image captured
from a VM that once ran in SSH mode, which carries a leftover copy: every clone of such an image
would register under the captured VM's instance id, no agent would be matched to the instance that
started it, and nothing in the log would say why. The daemon wrapper deletes any copy from `/tmp`
and the agent directory on every boot, before it starts the agent, which closes that path.

Prepare the image once, inside the VM, after installing and connecting the agent. The script lives
in this repository, so copy it over first. `orka3 vm list` gives the VM's IP and SSH port:

```bash
scp -P <ssh-port> scripts/install-teamcity-agent-daemon.sh <user>@<vm-ip>:/tmp/
```

Then run it in the VM, not on your machine:

```bash
ssh -t -p <ssh-port> <user>@<vm-ip> \
  'sudo bash /tmp/install-teamcity-agent-daemon.sh --agent-dir /Users/<user>/BuildAgent --user <user>'
```

Then, still in the VM, stop the agent, clear `logs/` and `temp/`, and strip the identity TeamCity
wrote into `conf/buildAgent.properties` before saving the VM as an image:

```bash
sed -i '' -E 's/^([[:space:]]*(name|authorizationToken)[[:space:]]*=).*/\1/' \
  /Users/<user>/BuildAgent/conf/buildAgent.properties
```

That blanks the values and leaves the keys, which is the shape
`conf/buildAgent.dist.properties` ships with: a blank `name` is how you tell the server to
generate one, and a blank `authorizationToken` is filled in on the next connection.

Both property lines matter. TeamCity generates the agent name on first start and writes it back to
that file, and it stores the authorization token there too. If either survives image capture, every
VM cloned from the image claims the same identity, and the second one to boot is refused with
*"another agent with the same authorization token and name is registered on the server"* until the
first disappears.

The daemon runs a wrapper, `/usr/local/libexec/teamcity-agent-service.sh`, rather than
`agent.sh` directly. `agent.sh start` forks and exits, so launchd would consider the job
finished and would have nothing to signal at shutdown. The wrapper stays in the foreground
and traps `SIGTERM`, at which point it runs `agent.sh stop force` so the agent unregisters
from the server. The plist allows 60 seconds (`ExitTimeOut`) for that to complete.

The wrapper retries `agent.sh start` rather than exiting when it fails, since a dependency may not
be up yet on first boot, and it skips the start if the agent is already running. `KeepAlive` is set
to `SuccessfulExit: false` so launchd respawns the wrapper if it dies unexpectedly but leaves it
alone after a clean stop, which would otherwise restart the agent during shutdown.

This only helps on `launchctl bootout` and real OS shutdowns. Orka's VM delete is a hard
power-off, so on the normal termination path launchd never runs the stop handler and the
agent still disappears without unregistering. TeamCity surfaces that as a health warning on
the image:

> The cloud image instance was not stopped properly. The respective build agent will continue
> being registered in TeamCity until the timeout and therefore might get assigned new builds.

Expect it on every daemon-mode termination. In idle-termination testing nothing followed from
it and the agent dropped off the server promptly. Terminating an agent mid-build has not been
validated; it costs a truncated build log, lost in-flight artifact uploads and a build failing as
disconnected, which is the same exposure SSH mode has whenever its best-effort stop fails.
Closing the gap properly needs a graceful guest shutdown from Orka before the VM is destroyed,
at which point the stop handler above starts working with no plugin change.

Exercise the stop path on a running VM without rebooting it, again from inside the VM:

```bash
sudo launchctl bootout system/jetbrains.teamcity.BuildAgent
grep -i unregister /Users/<user>/BuildAgent/logs/teamcity-agent.log
```

A LaunchDaemon has no GUI session, so iOS Simulator, UI tests and the login keychain do not
work under it. Those need auto-login plus a LaunchAgent instead. SSH mode has the same
limitation — only auto-login fixes it.

The VM's custom metadata carries only the TeamCity image id and the starting instance id. It is
readable by anyone who can read the VM in that namespace and by anything running inside the VM,
so do not extend it with secrets.

To validate the setup script without installing anything. This one does run on your machine, not
in a VM — it renders the plist and wrapper into a temp dir and exercises them against a stub agent:

```bash
./scripts/test-install-teamcity-agent-daemon.sh
```

## Build requirements

- JDK 11 or later (JDK 11, 17, or 21 recommended)
- Gradle 8.5 (included via wrapper)

### Recent Updates (2025)

This plugin has been upgraded to modern tooling:

- **Gradle**: 5.6.3 → 8.5
- **TeamCity**: 2018.1 → 2023.11
- **TeamCity Gradle Plugin**: 1.2.2 → 1.5.2 (now using `io.github.rodm` plugin IDs)
- **Java Support**: Now supports Java 11, 17, and 21
- **Test Framework**: Updated Mockito to 5.8.0 for modern Java compatibility
- **Checkstyle**: Updated to 10.12.5 with simplified Google Java Style configuration

## Building, packaging and testing the plugin

To build the plugin, run:

    ./gradlew build

This builds the plugin, runs checkstyle validation, and runs all tests. The output is in `macstadium-orka-server/build/distributions/`.

To run tests only:

    ./gradlew test

To run checkstyle only:

    ./gradlew check

### Running the plugin locally

#### First-time setup

To download and install a TeamCity server locally, run:

    ./gradlew macstadium-orka-server:downloadTeamcity202311
    ./gradlew macstadium-orka-server:installTeamcity202311

#### Starting the server

To start the TeamCity server with the plugin installed, run:

    ./gradlew macstadium-orka-server:startTeamcity202311Server

Or start it directly:

    cd macstadium-orka-server/servers/TeamCity-2023.11
    ./bin/teamcity-server.sh start

The TeamCity server will be available at <http://localhost:8111>

#### Stopping the server

To stop the server, run:

    ./gradlew macstadium-orka-server:stopTeamcity202311Server

Or stop it directly:

    cd macstadium-orka-server/servers/TeamCity-2023.11
    ./bin/teamcity-server.sh stop

### Setting Java Version

If you have multiple Java versions installed, ensure you're using Java 11 or later:

    export JAVA_HOME=$(/usr/libexec/java_home -v 11)
    java -version

### Troubleshooting

- **Build fails with Java version errors**: Ensure `JAVA_HOME` is set to Java 11 or later
- **TeamCity won't start**: Check that port 8111 is not already in use: `lsof -i :8111`
- **Tests fail with Mockito errors**: Ensure you're using Java 11+ (Mockito 5.x requires Java 11+)
- **Checkstyle warnings**: Checkstyle is configured with `ignoreFailures = true`, so warnings won't fail the build

[tutorial]: https://plugins.jetbrains.com/docs/teamcity/developing-teamcity-plugins.html
[gradle-plugin]: https://github.com/rodm/gradle-teamcity-plugin
