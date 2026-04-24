# Shell Completion

The CLI can generate completion scripts at runtime:

- Bash: `sonar-analyzer-cli completion --shell bash`
- Zsh: `sonar-analyzer-cli completion --shell zsh`

## Requirement

Completion is registered against a bare command name. Do not use a path like `./bin/sonar-analyzer-cli` as the completion command name.

If you use the repo-local launcher, expose it through an alias or put `bin/` on your `PATH`:

```bash
alias sonar-analyzer-cli="$PWD/bin/sonar-analyzer-cli"
```

## Bash

Load it for the current shell:

```bash
source <(sonar-analyzer-cli completion --shell bash)
```

Persist it in `~/.bashrc`:

```bash
echo 'source <(sonar-analyzer-cli completion --shell bash)' >> ~/.bashrc
```

## Zsh

Load it for the current shell:

```zsh
source <(sonar-analyzer-cli completion --shell zsh)
```

Persist it in `~/.zshrc`:

```zsh
echo 'source <(sonar-analyzer-cli completion --shell zsh)' >> ~/.zshrc
```

The generated Zsh script enables `bashcompinit` automatically, so you do not need to wire that manually.

## Custom Command Names

If you want completion for a different bare command name, use `--command-name`:

```bash
alias sa="$PWD/bin/sonar-analyzer-cli"
source <(sonar-analyzer-cli completion --shell bash --command-name sa)
```

## What Completes

Completion includes:

- top-level commands and subcommands
- flags and options
- analyzer ids such as `js` and `java`
- rule families such as `javascript`, `typescript`, `css`, and `java`
- rule selectors for `rules show`, `--enable`, and `--disable`
- managed profile names for profile commands
- packaged profile names for `profile save --from-profile`
- file path completion for file-oriented options such as `--from-file`

Rule selector completions are generated from the bundled rule catalog, so they stay aligned with the version of the analyzers shipped in the jar.

Managed profile completions are generated when you source the script. If you add or delete profiles in the current shell session, source the completion command again to refresh the candidate list.
