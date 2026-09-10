# Orka by MacStadium TeamCity Plugin

This readme provides information about how to build, package, or run the plugin locally. For usage information, see the TeamCity plugin [tutorial][tutorial].

The plugin provides TeamCity cloud integration with Orka by MacStadium. This allows users to configure TeamCity, so it can provision and tear down agents running in Orka on demand.  

The plugin uses [gradle plugin][gradle-plugin] to build and package the plugin. For more information, see the gradle plugin [page][gradle-plugin].

## Agent setup modes

The cloud profile picks how the TeamCity agent inside the VM is started and how it learns
which cloud instance it belongs to. All three produce the same result: an agent that
registers with `cloud.orka.instance.id` and `cloud.orka.image.id` set, so TeamCity can match
it to the instance it started.

| Mode | Starts the agent | Carries the identity | Needs SSH | Needs image prep | Stops the agent |
|---|---|---|---|---|---|
| SSH | server, over SSH | file SCP'd to `/tmp` | yes | agent installed, `serverUrl` set, SSH on | yes, `agent.sh stop` |
| Userdata | first-boot script in the VM | script writes the same file | no | same as SSH | no |
| Daemon | LaunchDaemon in the image | Orka metadata service | no | plus `install-teamcity-agent-daemon.sh` | no |

`serverUrl` must be set in `conf/buildAgent.properties` in every mode — unlike the EC2
integration, this plugin does not supply it.

### SSH

The original behaviour. The server waits for the VM's SSH port, uploads a metadata file and
runs `agent.sh start`. On termination it runs `agent.sh stop` before deleting the VM.

### Userdata

The server passes a base64 first-boot script as Orka userdata at deploy time. The script
reads its own VM name from the metadata service, writes the same metadata file the agent
plugin already looks for, and starts the agent as the configured VM user. No SSH connection
is ever made.

Apple Silicon only — userdata is ignored on Intel nodes, and the agent will simply never
register. Works with images already prepared for SSH mode.

### Daemon

The VM image starts the agent itself, and the agent-side plugin reads its identity from the
Orka metadata service (`orka_vm_name`, plus `teamcity_image_id` and
`teamcity_starting_instance_id` passed as custom metadata on deploy). The server only
deploys and deletes the VM.

Prepare the image once, inside the VM, after installing and connecting the agent:

```bash
sudo ./scripts/install-teamcity-agent-daemon.sh --agent-dir /Users/admin/BuildAgent --user admin
```

Then stop the agent, clear `logs/` and `temp/`, remove `name` from `buildAgent.properties`
and save the VM as an image.

The daemon runs a wrapper, `/usr/local/libexec/teamcity-agent-service.sh`, rather than
`agent.sh` directly. `agent.sh start` forks and exits, so launchd would consider the job
finished and would have nothing to signal at shutdown. The wrapper stays in the foreground
and traps `SIGTERM`, at which point it runs `agent.sh stop force` so the agent unregisters
from the server. The plist allows 60 seconds (`ExitTimeOut`) for that to complete.

This only helps on `launchctl bootout` and real OS shutdowns. Orka's VM delete is a hard
power-off, so on the normal termination path launchd never runs the stop handler and the
agent still disappears without unregistering. TeamCity reports that as *"The cloud image
instance was not stopped properly"*. Closing that gap needs a graceful guest shutdown from
Orka before the VM is destroyed.

Exercise the stop path on a running VM without rebooting it:

```bash
sudo launchctl bootout system/jetbrains.teamcity.BuildAgent
grep -i unregister /Users/admin/BuildAgent/logs/teamcity-agent.log
```

A LaunchDaemon has no GUI session, so iOS Simulator, UI tests and the login keychain do not
work under it. Those need auto-login plus a LaunchAgent instead. Note that SSH and Userdata
modes have the same limitation — only auto-login fixes it.

The auth token travels in the VM's custom metadata, which is readable by anyone who can read
the VM in that namespace, and by anything running inside the VM.

To validate the setup script without installing anything:

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
