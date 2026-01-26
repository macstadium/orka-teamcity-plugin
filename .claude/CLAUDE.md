# Orka TeamCity Plugin - Development Guide

## Project Overview

TeamCity cloud plugin for automatic management of Mac build agents via Orka by MacStadium. The plugin enables on-demand provisioning and teardown of virtual machines with TeamCity build agents.

## Project Structure

```
ci-orka-teamcity-plugin/
├── macstadium-orka-server/     # Server-side plugin (main logic)
├── macstadium-orka-agent/      # Agent-side plugin (minimal code)
├── macstadium-orka-common/     # Shared code (constants)
├── wiki/                       # Documentation
├── config/                     # Checkstyle configuration
├── build.gradle                # Root build file
└── settings.gradle             # Gradle settings
```

## Technology Stack

- **Language:** Java 21 (compatibility target)
- **Build JDK:** 21
- **Build System:** Gradle 8.5 (wrapper)
- **TeamCity API:** 2024.12
- **HTTP Client:** OkHttp 3.14.2
- **JSON:** Gson 2.8.5
- **SSH:** SSHJ 0.27.0
- **AWS SDK:** v2.21.0 (for EKS tokens)
- **Testing:** TestNG + Mockito 5.14.2
- **Code Style:** Checkstyle 10.12.5

## Build Commands

**Important:** Requires Java 21 for building. Set JAVA_HOME if needed:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

```bash
# Full build with tests
./gradlew clean build

# Run tests only (56 tests)
./gradlew test

# Compile without tests
./gradlew compileJava

# Checkstyle validation
./gradlew check

# Run specific test
./gradlew test --tests "com.macstadium.orka.OrkaCloudClientTest"
```

## Local Development

```bash
./gradlew installteamcity20181   # Install TeamCity locally
./gradlew startteamcity20181     # Start server (http://localhost:8111)
./gradlew stopteamcity20181      # Stop server
```

## Build Artifacts

- Server plugin: `macstadium-orka-server/build/distributions/orka-macstadium.zip` (~5.4 MB)
- Agent plugin: `macstadium-orka-agent/build/distributions/orka-macstadium.zip` (~4.3 KB)

## Key Components

### Server Module (`macstadium-orka-server/`)

**Core Classes:**

| File | Description |
|------|-------------|
| `OrkaCloudClient.java` | Main cloud client (1143 lines). VM management, capacity checks, recovery |
| `OrkaCloudImage.java` | Cloud image representation (VM configuration) |
| `OrkaCloudInstance.java` | Running VM instance representation |
| `OrkaLegacyCloudInstance.java` | Legacy instances from previous configurations |
| `OrkaClient.java` | REST API client for Orka |
| `RemoteAgent.java` | SSH-based agent management (start/stop) |
| `LegacyInstancesStorage.java` | Singleton for instance persistence across profile reloads |

**Background Tasks:**
- `RemoveFailedInstancesTask.java` - cleanup of failed instances (every 5 min)
- `GracefulShutdownTask.java` - graceful shutdown of legacy instances

**API Client (`client/` package):**
- `TokenProvider` - authentication interface
- `StaticTokenProvider` - static bearer token
- `AwsEksTokenProvider` - AWS IAM for EKS

### Agent Module (`macstadium-orka-agent/`)

- `OrkaAgent.java` - agent initialization, metadata reading

### Common Module (`macstadium-orka-common/`)

- `CommonConstants.java` - shared constants and validation patterns

## Architecture Details

### Capacity Management
- Checks CPU/RAM requirements against available node resources
- Considers node tags and pinned nodes
- Tracks running VMs per node (important for ARM: 2 VM limit)
- Capacity caching with 30-second TTL
- Backoff mechanism after failed checks

### Instance Recovery
- `LegacyInstancesStorage` - preserves instances during profile reload
- `CloudState` - storage in TeamCity database
- Orka API metadata - `tc_profile_id` and `tc_image_id` tags

### SSH Management
- SSH availability wait: 12 retries (120 seconds)
- Updates `buildAgent.properties` via SSH
- Starts TeamCity agent after VM is ready

## Development Guidelines

### Code Style
- Follow checkstyle configuration (`config/checkstyle/checkstyle.xml`)
- Some warnings in legacy code are acceptable (`ignoreFailures = true`)

### Testing
- All tests use TestNG + Mockito
- Real Orka environment not required (mocks used)
- Test path: `*/src/test/java/`

### Git Conventions
- Do NOT add `Co-Authored-By: Claude` to commits
- Follow existing commit message style

### Before Submitting Changes
1. Ensure `./gradlew test` passes
2. Ensure `./gradlew build` succeeds
3. Add tests for new functionality
4. Follow existing code patterns

## Plugin Configuration Parameters

### Required
- Orka endpoint URL
- Orka authentication token
- VM configuration name
- VM username/password (SSH)
- Maximum instances limit
- Agent pool ID
- Agent installation directory

### Optional
- Server URL (override)
- Namespace (default: orka-default)
- VM metadata (key=value format)
- Node IP mappings (private-to-public)
- AWS IAM authentication (cluster name, region)

## TeamCity API Compatibility

### Supported Versions
- **Minimum:** TeamCity 2024.12
- **Tested with:** TeamCity 2025.07.3
- **Future ready:** TeamCity 2026.1+ (requires Java 21 - already supported)

### Implemented Interfaces
```java
CloudClientEx        // Main cloud client interface
CloudClientFactory   // Factory for creating cloud clients
CloudImage           // VM configuration representation
CloudInstance        // Running VM instance representation
```

### API Notes
- `canStartNewInstanceWithDetails()` - new API method, fully implemented
- `canStartNewInstance()` - legacy method (deprecated since 2018.1), kept for compatibility
- No deprecated interfaces used in core code

### Gradle Plugin Warning
The `com.github.rodm.teamcity-*` plugins v1.5.2 show deprecation warnings recommending `io.github.rodm.teamcity-*`. This is cosmetic and does not affect functionality.

## Known Limitations

- Requires bidirectional connectivity between TeamCity and Orka
- Agent push not supported
- SSH with password authentication required

## Security Considerations

### SSH Host Key Verification
**Location:** `RemoteAgent.java:115`, `OrkaCloudClient.java:649`

The code uses `PromiscuousVerifier` which disables SSH host key verification:
```java
ssh.addHostKeyVerifier(new PromiscuousVerifier());
```

**Risk:** Vulnerable to man-in-the-middle attacks. An attacker could intercept VM credentials.

**Mitigation options:**
- Acceptable for isolated/trusted networks
- For production: implement known_hosts verification or trust-on-first-use (TOFU)

### Credential Handling
- VM passwords are passed via TeamCity Cloud Profile parameters
- AWS credentials use IRSA or default provider chain (no hardcoded secrets)
- Orka tokens stored in TeamCity secure storage

## Critical Code Areas

When modifying these areas, ensure thorough testing:

| Area | File | Risk | Tests Required |
|------|------|------|----------------|
| Node mappings parsing | `OrkaCloudClient.java:155-162` | Crash on malformed input | Unit tests exist |
| Instance initialization | `OrkaCloudInstance.java` constructor | NPE if fields not initialized | Unit tests exist |
| Token caching | `AwsEksTokenProvider.java:61-62` | Race condition | Needs AWS credentials |
| Temp file handling | `RemoteAgent.java:47-68` | Resource leak | Manual testing |
| SSH connections | `RemoteAgent.java`, `SSHUtil.java` | Connection failures | Manual testing on Orka |
| Capacity checking | `OrkaCloudClient.java:361-392` | Race conditions | Unit tests exist |

## Documentation

- [Setup Guide](wiki/setup.md) - installation and base image configuration
- [Usage Guide](wiki/usage.md) - usage and troubleshooting
- [How It Works](wiki/how-it-works.md) - internal mechanics

## Development Workflow

### Making Changes

1. **Create a feature branch** (optional for small changes)
   ```bash
   git checkout -b feature/my-change
   ```

2. **Make code changes** following existing patterns

3. **Run validation before committing:**
   ```bash
   ./gradlew compileJava   # Verify compilation
   ./gradlew test          # Run all tests (56 tests)
   ./gradlew check         # Checkstyle validation
   ```

4. **Full build verification:**
   ```bash
   ./gradlew clean build   # Complete build with all checks
   ```

### Testing Strategy

| Test Type | Command | Description |
|-----------|---------|-------------|
| All tests | `./gradlew test` | Runs all 56 unit tests |
| Specific test | `./gradlew test --tests "ClassName"` | Run single test class |
| Specific method | `./gradlew test --tests "ClassName.methodName"` | Run single test method |
| With verbose output | `./gradlew test --info` | Detailed test output |

**Test locations:**
- `macstadium-orka-server/src/test/java/` - main test directory
- Key test files:
  - `OrkaCloudClientTest.java` - cloud client tests (incl. node mappings validation)
  - `OrkaCloudInstanceTest.java` - instance lifecycle tests
  - `OrkaCloudImageTest.java` - image management tests
  - `CapacityCheckTest.java` - capacity logic tests
  - `RemoveFailedInstancesTaskTest.java` - background task tests
  - `AwsEksTokenProviderTest.java` - AWS token provider tests

**Test coverage notes:**
- Core components (`OrkaCloudClient`, `OrkaCloudImage`, `OrkaCloudInstance`) have good coverage
- `RemoteAgent`, `OrkaClient`, `SSHUtil` are mocked in tests - changes require manual testing

### Code Review Process

**Before requesting review:**
1. All tests pass: `./gradlew test`
2. Build succeeds: `./gradlew build`
3. No new checkstyle errors in your code

**Review checklist:**
- [ ] Code follows existing patterns and style
- [ ] New functionality has corresponding tests
- [ ] No hardcoded values (use constants)
- [ ] Error handling is appropriate
- [ ] No security vulnerabilities introduced
- [ ] Documentation updated if needed

**Critical review focus areas:**
- [ ] Input validation (especially for config parameters)
- [ ] Null safety (fields must be initialized)
- [ ] Thread safety (use `volatile` for shared state, `synchronized` for critical sections)
- [ ] Resource cleanup (close streams, delete temp files in `finally` blocks)
- [ ] Exception handling (don't swallow exceptions silently)

**Using Claude for code review:**
- Review unstaged changes: "Review my changes"
- Review specific file: "Review OrkaCloudClient.java"
- Review PR: `/review-pr <PR number>`
- Critical issues review: "Проведи ревью кода, фокус на критические ошибки"

### Commit Guidelines

- Use short, descriptive messages starting with a verb
- Examples from history:
  - `Added LegacyInstancesStorage`
  - `Change name buildagent`
  - `Recovery vm`
  - `Add check capacity`

## Common Code Patterns

### Adding a New Request Handler
See examples in `web/` package: `VmHandler.java`, `AgentPoolHandler.java`

### Adding a New API Endpoint
Extend `OrkaClient.java` and add corresponding Response DTOs in `client/` package

### Working with Instances
- Creation: via `OrkaCloudClient.startNewInstance()`
- Termination: via `OrkaCloudClient.terminateInstance()`
- Recovery: `OrkaCloudClient.recoverInstances()`

## Recent Changes

### Code Quality Improvements (2026-01)

**Fixed issues:**
| Issue | File | Fix |
|-------|------|-----|
| ArrayIndexOutOfBounds on malformed node mappings | `OrkaCloudClient.java:158` | Added filter for entries with `<2` parts |
| NPE - host field not initialized | `OrkaCloudInstance.java:44` | Initialize `host = ""` in constructor |
| Race condition in token cache | `AwsEksTokenProvider.java:61-62` | Added `volatile` modifier |
| Temp file resource leak | `RemoteAgent.java:47-68` | Added `finally` block with cleanup |

**New tests added:**
- `OrkaCloudClientTest` - malformed node mappings tests (3 tests)
- `OrkaCloudInstanceTest` - host initialization tests (3 tests)
- `AwsEksTokenProviderTest` - token provider validation tests (10 tests)

**Build system updates:**
- Java: 8 → 21
- Gradle: 7.6.4 → 8.5
- TeamCity API: 2022.04 → 2024.12
- Mockito: 4.11.0 → 5.14.2
- Checkstyle: 8.45.1 → 10.12.5
