# Managed Profiles

Managed profiles are saved rule configurations stored in your user config directory. They are separate from the packaged analyzer profiles listed by `rules profiles`.

## Where They Live

By default the CLI stores them in:

- Linux/XDG: `$XDG_CONFIG_HOME/sonar-analyzer-cli`
- Fallback: `~/.config/sonar-analyzer-cli`

The layout is:

- `profiles/<name>.yaml`: saved profile files
- `current-profile`: the currently selected managed profile name

For automation or testing you can override the config root with `SONAR_ANALYZER_CLI_CONFIG_DIR`.

## Basic Workflow

Create a profile from a packaged base profile:

```bash
sonar-analyzer-cli profile save frontend \
  --from-profile "Sonar way" \
  --enable typescript:S1186 \
  --disable javascript:S125 \
  --use
```

Create a profile from an existing rules file:

```bash
sonar-analyzer-cli profile save strict-java \
  --from-file ./rules/java-strict.yaml
```

Inspect and switch profiles:

```bash
sonar-analyzer-cli profile list
sonar-analyzer-cli profile current
sonar-analyzer-cli profile show frontend
sonar-analyzer-cli profile use strict-java
sonar-analyzer-cli profile clear
```

Delete one:

```bash
sonar-analyzer-cli profile delete strict-java
```

## How Analysis Resolves `--rules`

When you pass `--rules`, resolution happens like this:

1. If the value matches a managed profile name, that saved profile is used.
2. Otherwise if the value is an existing file path, that JSON/YAML rule file is used.
3. Otherwise the value is treated as a packaged analyzer profile name such as `Sonar way`.

If you need a file path that might be ambiguous, use an explicit path like `./rules.yaml`.

## Current Managed Profile

If you set a current managed profile with `profile use`, then:

- `analyze` uses it automatically when `--rules` is omitted
- `analyze-file` uses it automatically when `--rules` is omitted

If the current managed profile points to a missing file, analysis fails with a clear error so you do not silently fall back to a different rule set.

## Profile File Format

Managed profiles use the same YAML/JSON format as `--rules` files:

```yaml
profiles:
  - 'Sonar way'
enable:
  - 'typescript:S1186'
disable:
  - 'javascript:S125'
```

The CLI validates selectors when you save a profile. Bare `S...` keys are rejected; use prefixed selectors such as `java:S1068` or `typescript:S1186`.
