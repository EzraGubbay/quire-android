# Rulesets (source of truth for the hard rules)

Applied by the human in QMOB-E0-S4 — agents never apply or edit these.

```bash
gh api -X POST repos/EzraGubbay/quire-android/rulesets --input .github/rulesets/dev-rc.json
gh api -X POST repos/EzraGubbay/quire-android/rulesets --input .github/rulesets/main.json
```

- `dev-rc.json`: PR required, no deletion / force-push, required checks `build`, `lint`, `unit-tests`.
- `main.json`: the same plus `e2e`, and the branch must be up to date.
- The check names are the job names in `.github/workflows/ci.yml`. Rename one and you must rename it here.
- No bypass actors: the rules apply to everyone, including the repository admin.
- `require_code_owner_review` is off on purpose: with a solo maintainer the human merges every PR anyway, and a code
  owner cannot approve their own PR (it would block human-authored changes to `/.github/`). Turn it on, and raise
  `required_approving_review_count`, when there are collaborators.
