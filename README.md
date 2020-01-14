# Orka by MacStadium TeamCity Plugin

This readme provides information about how to build, package, or run the plugin locally. For usage information, see the TeamCity plugin [tutorial][tutorial].

The plugin provides TeamCity cloud integration with Orka by MacStadium. This allows users to to configure TeamCity, so it can provision and tear down agents running in Orka on demand.  

The plugin uses [gradle plugin][gradle-plugin] to build and package the plugin. For more information, see the gradle plugin [page][gradle-plugin].

## Build requirements

- JDK 8

## Building, packaging and testing the plugin

To build the plugin, run:

    ./gradlew build

This runs [checkstyle][checkstyle] validation and builds the plugin. The output is in `macstadium-orka-server/build/distributuons/`.

To run tests, run:

    ./gradlew test

To run checkstyle, run:

    ./gradlew check

## Running the plugin locally

To use the plugin locally, run:

    ./gradlew installteamcity20181

This installs a TeamCity server locally.

To start the server, run:

    ./gradlew startteamcity20181

This boots a TeamCity server, packages the plugin and installs it. To run the TeamCity server open http://localhost:8111.

To stop the server, run:

    ./gradlew stopteamcity20181

[checkstyle]: http://checkstyle.sourceforge.net/
[tutorial]: https://plugins.jetbrains.com/docs/teamcity/developing-teamcity-plugins.html
[gradle-plugin]: https://github.com/rodm/gradle-teamcity-plugin