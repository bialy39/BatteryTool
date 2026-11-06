# Contributors

## Original author

- **[Domi04151309](https://github.com/Domi04151309)** — Battery Tool itself
  ([upstream](https://github.com/Domi04151309/BatteryTool), GPL-3.0). Everything this fork is
  built on belongs to him.

## Maintainer of this fork

- **[A. Bialic (@bialy39)](https://github.com/bialy39)** — Android 17 / API 37 port, autostart
  blocking and watchdog, "Running apps" tab, app-id change, release signing and packaging.

## AI assistance

Parts of this fork are written in collaboration with an AI assistant. Every commit produced
that way is labelled in the commit message:

```
Co-authored-by: DeepSeek <noreply@deepseek.com>
```

- **DeepSeek** (model `deepseek-flash`, driven through Hermes Agent) — draft code, docs,
  review notes and release notes.

DeepSeek is a model provider, not a GitHub user, so it does not appear in the repository's
Contributors graph (`Insights → Contributors`) — that graph only counts commit *authors* whose
e-mail is linked to a GitHub account. The trailers are the canonical record:

```sh
git log --format='%h %s — %(trailers:key=Co-authored-by,valueonly)' | grep 'DeepSeek'
```

## Attribution policy

- AI-assisted commits always carry the `Co-authored-by` trailer above; the git hook that adds
  it belongs to the maintainer's machine setup, not to this repository.
- The assistant has no GitHub account, no token of its own and cannot merge, release or publish.
  Every push goes through the maintainer's authenticated `gh` session, and he reviews the change
  before it lands.
- Bugs, licensing (GPL-3.0 obligations) and security decisions remain the maintainer's
  responsibility.
