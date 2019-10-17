# Orka by MacStadium TeamCity Plugin

This readme provides information about how to build, package, or run the plugin locally. For usage information, see the TeamCity plugin [tutorial][tutorial].

The plugin provides TeamCity cloud integration with Orka by MacStadium. This allows users to to configure TeamCity, so it can provision and tear down agents running in Orka on demand.

## Build requirements

- [Maven 3][maven]
- JDK 8

## Building, packaging and testing the plugin

To build the plugin, run:

    mvn install

This runs [checkstyle][checkstyle] validation and builds the plugin.

To package the plugin, run:

    mvn package

This runs checkstyle validation, build and package the plugin.
It produces an `hpi` file located in the `target` folder.

To run tests, run:

    mvn test

To run checkstyle, run:

    mvn validate

## Running the plugin locally

To use the plugin locally, run:

    mvn package tc-sdk:start

This boots a TeamCity server, packages the plugin and installs it. To run the TeamCity server open http://localhost:8111.

To stop the server, run:

    mvn package tc-sdk:stop

[maven]: http://maven.apache.org/
[checkstyle]: http://checkstyle.sourceforge.net/
[tutorial]: https://plugins.jetbrains.com/docs/teamcity/developing-teamcity-plugins.html
