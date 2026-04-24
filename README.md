# sonar-analyzer-cli

Standalone Sonar analyzer CLI for local JS, TypeScript, CSS, and Java analysis with a modular structured-monolith layout and shared core logic.

## Modules

- `sonar-cli-core`: shared catalog, scan, report, and analyzer contracts
- `sonar-cli-js`: SonarJS and SonarCSS integration
- `sonar-cli-java`: SonarJava integration
- `sonar-cli-app`: unified CLI and shaded application jar

## Build

```bash
mvn package
```

## Run

```bash
./bin/sonar-analyzer-cli rules list
./bin/sonar-analyzer-cli rules show typescript:S1186
./bin/sonar-analyzer-cli rules profiles
./bin/sonar-analyzer-cli profile save frontend --enable typescript:S1186 --use
./bin/sonar-analyzer-cli analyze .
./bin/sonar-analyzer-cli analyze-file path/to/file.java --analyzer java
./bin/sonar-analyzer-cli doctor . --analyzer java
./bin/sonar-analyzer-cli completion --shell bash
```

## Commands

- `rules list`: list packaged rules, optionally filtered by `--family` or `--analyzer`
- `rules show <selector>`: inspect one prefixed rule selector
- `rules profiles`: list packaged quality profiles bundled by the analyzers
- `profile list|current|show|save|use|clear|delete`: manage saved local profiles under your user config directory
- `analyze <dir>`: scan a directory recursively
- `analyze-file <file>`: analyze a single file while inferring a useful project root
- `doctor <dir>`: inspect runtime prerequisites, especially Java binaries and libraries
- `completion --shell bash|zsh`: generate shell completion scripts

## Rule Selectors

Only prefixed Sonar selectors are supported now:

- Java: `java:S1068`
- JavaScript: `javascript:S3776`
- TypeScript: `typescript:S6564`
- CSS: `css:S1128`

Bare `S...` keys are no longer accepted.

You can enable or disable rules directly:

```bash
./bin/sonar-analyzer-cli analyze . \
  --enable javascript:S3776,typescript:S1186,java:S1068 \
  --disable css:S1128
```

Or provide a JSON/YAML rules file:

```yaml
profiles:
  - 'Sonar way'
enable:
  - 'typescript:S1186'
disable:
  - 'css:S1128'
```

```bash
./bin/sonar-analyzer-cli analyze . --rules ./rules.yaml
```

## Managed Profiles

Packaged analyzer profiles and your own saved profiles are separate features:

- Use `rules profiles` to inspect packaged profiles such as `Sonar way`
- Use `profile save` to store your own profile once and reuse it later
- Use `profile use <name>` to set the current managed profile
- When `--rules` is omitted, the current managed profile is applied automatically

Examples:

```bash
./bin/sonar-analyzer-cli profile save frontend \
  --from-profile "Sonar way" \
  --enable typescript:S1186 \
  --disable javascript:S125 \
  --use

./bin/sonar-analyzer-cli profile list
./bin/sonar-analyzer-cli profile current
./bin/sonar-analyzer-cli profile show frontend
./bin/sonar-analyzer-cli analyze . --rules frontend
```

Detailed profile workflow: [docs/profiles.md](docs/profiles.md)

## Shell Completion

The CLI can generate runtime completion scripts for Bash and Zsh.

```bash
source <(sonar-analyzer-cli completion --shell bash)
source <(sonar-analyzer-cli completion --shell zsh)
```

Completion covers subcommands, flags, analyzers, rule families, managed profile names, packaged profile sources, and rule selectors. Options that take files still fall back to filename completion.

If you run the repo-local launcher, expose it as a bare command name first:

```bash
alias sonar-analyzer-cli="$PWD/bin/sonar-analyzer-cli"
source <(sonar-analyzer-cli completion --shell bash)
```

Detailed setup and persistence instructions: [docs/shell-completion.md](docs/shell-completion.md)

## Java Analysis

Java analysis is most accurate when the CLI can resolve:

- main compiled classes through `--java-binaries`
- dependency jars through `--java-libraries`
- test compiled classes through `--java-test-binaries`
- test dependency jars through `--java-test-libraries`

The CLI already auto-detects common outputs such as:

- `target/classes`
- `target/test-classes`
- `build/classes/java/main`
- `build/classes/java/test`
- jar files inside `dependency`, `dependencies`, `lib`, and `libs` directories under the analyzed root

Start with:

```bash
./bin/sonar-analyzer-cli doctor . --analyzer java
```

Detailed Maven, Gradle, and manual setup instructions: [docs/java-analysis.md](docs/java-analysis.md)

## Output UX

- Text output is grouped into `Summary`, `Files`, `Rules`, and `Warnings`
- Interactive terminals get ANSI color output
- File headers and related file paths are green for faster scanning
- Warnings are collected into a dedicated section instead of being mixed into JSON output

## SonarQube For IDE Parity

- Canonical Sonar selectors work across Java, JavaScript, TypeScript, and CSS
- Shared JS/TS rules can still be selected with `typescript:S...` on TypeScript files
- Advanced Security and architecture selectors such as `tssecurity:` and `tsarchitecture:` are recognized, but standalone local analysis only warns when the required engines are not bundled
- `analyze-file` infers a project root from nearby markers such as `tsconfig.json`, `package.json`, `pom.xml`, Gradle files, and `.git`

## Packaging

The shaded application jar is written to:

```bash
sonar-cli-app/target/sonar-analyzer-cli.jar
```
