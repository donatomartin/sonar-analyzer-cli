# Java Binaries And Libraries

Java analysis is only fully semantic when SonarJava can resolve compiled classes and external dependencies.

## What The CLI Needs

For the best results, provide or let the CLI discover:

- `--java-binaries`: compiled main classes
- `--java-libraries`: dependency jars for main sources
- `--java-test-binaries`: compiled test classes
- `--java-test-libraries`: dependency jars for test sources
- `--java-jdk-home`: JDK home when the project must be analyzed against a specific JDK

If these are missing, you will still get some issues, but semantic and type-driven rules can be skipped or degraded.

## Start With Doctor

Always start here:

```bash
sonar-analyzer-cli doctor . --analyzer java
```

The doctor output shows:

- configured Java paths
- resolved Java paths after auto-detection
- warnings for missing binaries or libraries
- concrete remediation hints for Maven and Gradle projects

## What Is Auto-Detected

The CLI already discovers these common outputs when they exist under the analyzed root:

- `target/classes`
- `target/test-classes`
- `build/classes/java/main`
- `build/classes/java/test`
- jar files inside directories named `dependency`, `dependencies`, `lib`, or `libs`

If your build writes elsewhere, pass the paths explicitly.

## Maven

Compile classes first:

```bash
mvn -q -DskipTests compile test-compile
```

If you also want a local dependency directory that the CLI can discover or reference directly:

```bash
mvn -q dependency:copy-dependencies \
  -DincludeScope=compile \
  -DoutputDirectory=target/dependency
```

Then analyze:

```bash
sonar-analyzer-cli analyze . \
  --analyzer java \
  --java-binaries target/classes \
  --java-test-binaries target/test-classes \
  --java-libraries target/dependency
```

Notes:

- If `target/classes` and `target/test-classes` already exist, the CLI often discovers them automatically.
- `target/dependency` is useful because it is both explicit and auto-discoverable.
- If you need test-only jars separately, also pass `--java-test-libraries`.

## Gradle

Compile classes first:

```bash
./gradlew classes testClasses
```

If the project already copies jars into `build/dependencies`, `libs`, or `lib`, the CLI can usually pick them up automatically. If not, create a concrete copy task:

```kotlin
tasks.register<Copy>("copyRuntimeDeps") {
  from(configurations.runtimeClasspath)
  into(layout.buildDirectory.dir("dependencies"))
}

tasks.register<Copy>("copyTestRuntimeDeps") {
  from(configurations.testRuntimeClasspath)
  into(layout.buildDirectory.dir("test-dependencies"))
}
```

Run it:

```bash
./gradlew classes testClasses copyRuntimeDeps copyTestRuntimeDeps
```

Then analyze:

```bash
sonar-analyzer-cli analyze . \
  --analyzer java \
  --java-binaries build/classes/java/main \
  --java-test-binaries build/classes/java/test \
  --java-libraries build/dependencies \
  --java-test-libraries build/test-dependencies
```

## Multi-Module Builds

For Maven or Gradle multi-module repositories, you can pass multiple comma-separated paths:

```bash
sonar-analyzer-cli analyze . \
  --analyzer java \
  --java-binaries module-a/target/classes,module-b/target/classes \
  --java-libraries module-a/target/dependency,module-b/target/dependency
```

Use absolute paths if outputs live outside the analyzed root.

## Manual Or IDE Builds

If you are not using Maven or Gradle, collect:

- every directory containing compiled `.class` files
- every directory or jar that provides external classes used by the source

Then pass them explicitly:

```bash
sonar-analyzer-cli analyze src \
  --analyzer java \
  --java-binaries /abs/path/to/classes \
  --java-libraries /abs/path/to/libs
```

## Common Warning Meanings

`Compiled classes were not found`

- SonarJava could not resolve `sonar.java.binaries`.
- Fix by compiling first or passing `--java-binaries`.

`Dependencies/libraries were not resolved`

- SonarJava could not resolve external types.
- Fix by pointing `--java-libraries` at the jar directory or specific jars.

`Test bytecode was not found`

- Test-only Java rules may be incomplete.
- Fix by compiling tests and passing `--java-test-binaries`.

## When To Use `--java-jdk-home`

Use `--java-jdk-home` when:

- the project targets a JDK different from the one running the CLI
- the build uses vendor-specific or newer JDK classes that are not available from the host runtime

Example:

```bash
sonar-analyzer-cli analyze . \
  --analyzer java \
  --java-jdk-home /path/to/jdk-21
```
