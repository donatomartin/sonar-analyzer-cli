# sonar-analyzer-cli

Standalone Sonar analyzer CLI with a modular structured-monolith layout and shared core logic for `js` and `java`.

## Modules

- `sonar-cli-core`: shared catalog, scan, report, and analyzer contracts
- `sonar-cli-js`: SonarJS and SonarCSS integration
- `sonar-cli-java`: SonarJava integration
- `sonar-cli-app`: unified CLI and shaded application jar

The codebase now uses the `com.donatomartin` domain/package namespace.

## Build

```bash
mvn package
```

## Run

```bash
./bin/sonar-analyzer-cli rules list
./bin/sonar-analyzer-cli rules list --analyzer java
./bin/sonar-analyzer-cli rules show S3776
./bin/sonar-analyzer-cli rules profiles
./bin/sonar-analyzer-cli doctor .
./bin/sonar-analyzer-cli analyze .
./bin/sonar-analyzer-cli analyze . --analyzer js,java
./bin/sonar-analyzer-cli analyze-file path/to/file.java --analyzer java
```

`bin/sonarjs-cli` is kept as a compatibility alias and forwards to the new launcher.

## Rule Selection

The CLI now exposes canonical Sonar-style selectors:

- Java: `java:S1068`
- JavaScript: `javascript:S3776`
- TypeScript: `typescript:S6564`
- CSS: `css:S1128`

Raw `S...` keys and the legacy `js:S...` selectors still resolve as aliases.

You can enable or disable rules directly:

```bash
./bin/sonar-analyzer-cli analyze . --enable javascript:S3776,java:S1068 --disable css:S1128
```

Or provide a JSON/YAML rules file:

```yaml
profiles: []
enable:
  - javascript:S3776
  - java:S1068
disable:
  - css:S1128
```

```bash
./bin/sonar-analyzer-cli analyze . --rules rules.yaml
```

If you want to inspect packaged quality profiles, use:

```bash
./bin/sonar-analyzer-cli rules profiles
```

## Java Analysis Notes

- Java analysis works best when compiled classes are available through `--java-binaries`.
- The CLI now auto-detects common build outputs such as `target/classes`, `target/test-classes`, `build/classes/java/main`, and `build/classes/java/test` when they already exist.
- Doctor output and analysis warnings now explain how to fix missing binaries and libraries, including Maven and Gradle compile hints.
- You can also pass `--java-libraries`, `--java-test-binaries`, `--java-test-libraries`, `--java-source`, and `--java-jdk-home`.
- The CLI discovers `.java`, `.js`, `.ts`, `.tsx`, `.css`, `.html`, and `.yaml`/`.yml` files and routes them to the appropriate analyzer module.

## Output UX

- Text reports are grouped by file and rule and include a warnings section.
- `analyze-file` now infers a project root from nearby markers such as `tsconfig.json`, `package.json`, `pom.xml`, and Gradle files so TypeScript and Java scans keep more of their project context.

## SonarQube For IDE Parity

- The CLI accepts SonarQube for IDE style selectors such as `typescript:S1186`.
- Advanced Security (`jssecurity:` / `tssecurity:`) and architecture (`jsarchitecture:` / `tsarchitecture:`) selectors are recognized, but standalone local analysis still skips them when the bundled analyzers do not provide those rule engines. The report warns instead of failing hard.

## Packaging

The shaded application jar is written to:

```bash
sonar-cli-app/target/sonar-analyzer-cli.jar
```
