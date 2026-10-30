# NB-Ecom-API

Spring Boot 3.3 / Java 21 backend for NartureBelle. The live API runs on Vercel (Container runtime, project
`nb-ecom-api`) at `https://nb-ecom-api.vercel.app/api/v1`; `HANDOFF.md` has the history and `UI_INTEGRATION_GUIDE.md`
the contracts.

## Branching workflow

Every new feature or change gets its own branch, so each one can be tracked from start to release. The UI repo
(NB-Ecomm-UI) follows the same rules.

| Branch | Purpose |
|---|---|
| `main` | Production. Vercel deploys it to the live site. Changes arrive only by pull request from `develop`. |
| `develop` | Integration. Every feature branch starts from it and merges back into it. |
| `feature/<short-name>` | One new feature or change, e.g. `feature/careers-hello-mailbox`. |
| `fix/<short-name>` | A bug fix. |
| `docs/<short-name>` | Documentation only. |

**Starting work** — always from the latest `develop` on GitHub, never from a local or older branch:

```
git fetch origin
git switch -c feature/<short-name> origin/develop
```

**One feature at a time.** A branch holds all the code for its one feature, and it is finished before the next branch
starts:
1. Build the feature on its branch. Every change for that feature goes on this same branch; don't open a second branch for it.
2. Tests pass (`./gradlew test`), then push: `git push -u origin feature/<short-name>`.
3. **The product owner checks how the application behaves** with the change (and asks for fixes on the same branch if needed).
4. Once they approve, open a pull request **into `develop`** and merge it.
5. Only then create the next feature branch, from the updated `origin/develop`, so it already contains the previous feature.
6. To release, open a pull request from **`develop` into `main`** and merge it; Vercel then deploys production.

Rules:
- Don't commit to `develop` or `main` directly, and don't reuse an old branch for a new feature.
- Don't start a new feature branch while another is unmerged, and don't build one feature branch on top of another.
- If `develop` moves on while a branch is open, bring it in with `git merge origin/develop` before the pull request.
- `firebase-setup` and older branches (`master`, `codestepup`, `Added_Split_IO`) are history; don't start new work from them.
- Note each feature in `HANDOFF.md` / `UI_INTEGRATION_GUIDE.md` on its own branch, so the docs ship with the code.
