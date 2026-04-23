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
./bin/sonar-analyzer-cli doctor .
./bin/sonar-analyzer-cli analyze .
./bin/sonar-analyzer-cli analyze . --analyzer js,java
./bin/sonar-analyzer-cli analyze-file path/to/file.java --analyzer java
```

`bin/sonarjs-cli` is kept as a compatibility alias and forwards to the new launcher.

## Rule Selection

You can enable or disable rules directly:

```bash
./bin/sonar-analyzer-cli analyze . --enable S3776,java:S1068 --disable css:S1128
```

Or provide a JSON/YAML rules file:

```yaml
profiles: []
enable:
  - S3776
  - java:S1068
disable:
  - css:S1128
```

```bash
./bin/sonar-analyzer-cli analyze . --rules rules.yaml
```

If a raw rule key exists in multiple analyzers or families, use the selector form such as `css:S1128` or `java:S1068`.

## Java Analysis Notes

- Java analysis works best when compiled classes are available through `--java-binaries`.
- You can also pass `--java-libraries`, `--java-test-binaries`, `--java-test-libraries`, `--java-source`, and `--java-jdk-home`.
- The CLI discovers `.java`, `.js`, `.ts`, `.tsx`, `.css`, `.html`, and `.yaml`/`.yml` files and routes them to the appropriate analyzer module.

## Packaging

The shaded application jar is written to:

```bash
sonar-cli-app/target/sonar-analyzer-cli.jar
```
