# Orka by MacStadium TeamCity Plugin

This readme provides information about how to build, package, or run the plugin locally. For usage information, see the TeamCity plugin [tutorial][tutorial] and [setup guide](wiki/setup.md).

The plugin provides TeamCity cloud integration with Orka by MacStadium. This allows users to configure TeamCity, so it can provision and tear down agents running in Orka on demand.  

The plugin uses [gradle plugin][gradle-plugin] to build and package the plugin. For more information, see the gradle plugin [page][gradle-plugin].

## Build requirements

- JDK 17 (Java 17)
- Gradle 7.6.4 (included via wrapper)

**Note:** The project is compiled with Java 8 compatibility (source/target) but requires JDK 17 to build.

## Project structure

The plugin consists of three modules:

- `macstadium-orka-server` - Server-side plugin (main functionality)
- `macstadium-orka-agent` - Agent-side plugin
- `macstadium-orka-common` - Shared code between server and agent

## Building, packaging and testing the plugin

### Clean and build everything

To clean previous builds and build the plugin from scratch:

    ./gradlew clean build

This compiles the code, runs [checkstyle][checkstyle] validation, executes tests, and packages the plugin.

### Build output

The built plugins are located in:

- Server plugin: `macstadium-orka-server/build/distributions/orka-macstadium.zip` (~5.4 MB)
- Agent plugin: `macstadium-orka-agent/build/distributions/orka-macstadium.zip` (~4.3 KB)

### Run tests only

To run all tests:

    ./gradlew test

All 34 tests should pass successfully.

### Compile without tests

To compile the code without running tests:

    ./gradlew compileJava compileTestJava

### Run checkstyle validation

To run checkstyle validation:

    ./gradlew check

**Note:** Some checkstyle warnings are expected (indentation and import order issues in legacy code). These don't block the build.

## Running the plugin locally

To use the plugin locally, run:

    ./gradlew installteamcity20181

This installs a TeamCity server locally.

To start the server, run:

    ./gradlew startteamcity20181

This boots a TeamCity server, packages the plugin and installs it. To run the TeamCity server open <http://localhost:8111>.

To stop the server, run:

    ./gradlew stopteamcity20181

## Deployment

To deploy the plugin to a TeamCity server:

1. Build the plugin using `./gradlew build`
2. Upload the server plugin zip file from `macstadium-orka-server/build/distributions/orka-macstadium.zip`
3. In TeamCity, go to `Administration` → `Plugins List` → `Upload plugin zip`
4. Choose the zip file and upload
5. Restart the TeamCity server if required

For detailed setup instructions, see [setup guide](wiki/setup.md) and [usage guide](wiki/usage.md).

## Troubleshooting

### Build issues

If you encounter build issues:

1. Clean the build: `./gradlew clean`
2. Ensure you're using JDK 17
3. Check that Gradle wrapper can download dependencies (network access required)

### Test failures

If tests fail, ensure:

- No real Orka environment is required for tests (tests use mocks)
- All test dependencies are properly resolved
- JDK 17 is being used

### Checkstyle warnings

Checkstyle warnings about indentation and import order are expected in legacy code. They are configured to not fail the build (`ignoreFailures = true`).

## Development

### Code style

The project uses checkstyle for code quality validation. Configuration is in `config/checkstyle/checkstyle.xml`.

### Testing

Tests are written using TestNG and Mockito. Test files are located in `*/src/test/java/`.

To run a specific test:

    ./gradlew test --tests "com.macstadium.orka.OrkaCloudClientTest"

### IDE Setup

The project can be imported into IntelliJ IDEA or Eclipse as a Gradle project.

## Known limitations

- The plugin requires bidirectional connectivity between TeamCity server and Orka environment
- Agent push is not supported
- SSH access must be enabled on Orka VMs

## Contributing

When contributing:

1. Ensure all tests pass: `./gradlew test`
2. Build successfully: `./gradlew build`
3. Follow existing code style
4. Add tests for new functionality

## Documentation

- [Setup Guide](wiki/setup.md) - Installation and base image configuration
- [Usage Guide](wiki/usage.md) - How to use the plugin and troubleshooting
- [How It Works](wiki/how-it-works.md) - Internal mechanics and VM triggers explanation

## References

- [TeamCity Plugin Development Tutorial][tutorial]
- [Gradle TeamCity Plugin][gradle-plugin]
- [Orka Documentation](https://orkadocs.macstadium.com/)
- [Checkstyle Documentation][checkstyle]

[checkstyle]: http://checkstyle.sourceforge.net/
[tutorial]: https://plugins.jetbrains.com/docs/teamcity/developing-teamcity-plugins.html
[gradle-plugin]: https://github.com/rodm/gradle-teamcity-plugin
