# Orka by MacStadium TeamCity Plugin

This readme provides information about how to build, package, or run the plugin locally. For usage information, see the TeamCity plugin [tutorial][tutorial].

The plugin provides TeamCity cloud integration with Orka by MacStadium. This allows users to configure TeamCity, so it can provision and tear down agents running in Orka on demand.  

The plugin uses [gradle plugin][gradle-plugin] to build and package the plugin. For more information, see the gradle plugin [page][gradle-plugin].

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
