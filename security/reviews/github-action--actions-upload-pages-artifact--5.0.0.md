---
component: "actions/upload-pages-artifact"
kind: github-action
version: "5.0.0"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: the build job of the Pages workflow (push to main, GitHub-hosted Linux runner), pinned as actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9; it runs its nested actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f (v7.0.0), which has its own review"
conditions: [
  "uses: actions/upload-pages-artifact@fc324d3547104276b827a68afc52ff2a11cc49c9 (full SHA; never the v5 or v5.0.0 tag)",
  "The nested actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f (v7.0.0) stays allowlisted under its own review; if that review lapses, so does this one",
  "path is a dedicated, freshly generated site directory under the build output (never the workspace root, the checkout or $HOME)",
  "A step immediately before the upload fails the job if the site directory contains any symlink or hard link (find <path> -type l, and -links +1 on files): the action's tar uses --dereference --hard-dereference and would publish the target's content",
  "include-hidden-files stays unset or 'false'; name stays 'github-pages'; retention-days stays at the default of 1",
  "The build job runs only on push to main (never pull_request, pull_request_target or workflow_run), with permissions at most contents: read (plus pages: read for configure-pages), and actions/checkout with persist-credentials: false",
  "runs-on is a GitHub-hosted Linux runner (only the Linux tar branch is reviewed for our use)"
]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# actions/upload-pages-artifact v5.0.0

**Method.** On 2026-11-06 I read the GitHub API and, at commit `fc324d35…`, `action.yml` and `LICENSE`. The action is a composite with no JavaScript of its own. I read its single nested action, `actions/upload-artifact` at `bbbca2dd…`, separately: see `github-action--actions-upload-artifact--7.0.0.md`.

## 1. Verdict
**APPROVE-WITH-CONDITIONS.** This is GitHub's own action, at a verified commit. Its whole behaviour is in a 2,895-character `action.yml`: one `tar` command, then the nested upload-artifact, pinned by full SHA. Inputs reach the shell only through an environment variable, so the script has no expression injection. The material risk is **`tar --dereference --hard-dereference`**. A symlink inside the site directory is followed, and **the target's content is published on the public site**. That could be a link to a token file in `$RUNNER_TEMP`, `~/.m2/settings.xml` or anything else the runner user can read. A symlink-free check before upload is therefore a condition. The nested pin is upload-artifact **v7.0.0**, not the 7.0.2 the design approves for direct use, so it is reviewed on its own.

## 2. What it is and why it is needed
It packages the generated static site as the `github-pages` artifact that `deploy-pages` consumes. The design separates the build job from the deploy job, using reviewed SHA pins (`pages-design.md` §5).

## 3. Provenance
- **Publisher.** GitHub, org `actions`. The repo was created 2022-05-24, is MIT-licensed and is not archived ([repo](https://api.github.com/repos/actions/upload-pages-artifact)).
- **Tag.** `v5.0.0` is a lightweight tag on `fc324d3547104276b827a68afc52ff2a11cc49c9` ([ref](https://api.github.com/repos/actions/upload-pages-artifact/git/ref/tags/v5.0.0)). The floating [v5](https://api.github.com/repos/actions/upload-pages-artifact/git/ref/tags/v5) tag points at the same commit today.
- **Commit.** A merge of PR #139, authored by `tsusdere` and committed by `web-flow` at 2026-04-08T19:09:19Z. It is "verified" with reason `valid` ([commit](https://api.github.com/repos/actions/upload-pages-artifact/commits/v5.0.0)). Its only change is one line in `action.yml`: the nested pin moves from `upload-artifact@ea165f8d… # v4.6.2` to `@bbbca2dd… # v7.0.0`.
- **Release.** Published 2026-04-10T18:22:59Z by `tsusdere`, with `"immutable":false` and no assets ([release](https://api.github.com/repos/actions/upload-pages-artifact/releases/latest)). The body lists #139 and #137 (the `include-hidden-files` input). Its compare link points at `v4.0.0...v4.0.1`, which is a cosmetic error.
- **Signing.** There are no signed releases and no provenance. There is no build step: what runs is the YAML itself.

## 4. Maintenance and adoption
- **Releases** ([releases](https://api.github.com/repos/actions/upload-pages-artifact/releases?per_page=8)):
  - v3.0.1: 2024-02-07
  - v4.0.0: 2025-08-14. Dotfiles excluded by default, and upload-artifact pinned by SHA (#127).
  - v5.0.0: 2026-04-10
  - The last push was 2026-10-19.
- **Adoption:** 522 stars, 136 forks.
- **OpenSSF Scorecard 5.6**, via [deps.dev](https://api.deps.dev/v3/projects/github.com%2Factions%2Fupload-pages-artifact). The scan date is 2026-08-24, at commit `98ef48e4…`, not the tag.
  - Strong: Code-Review 10, Dangerous-Workflow 10, Binary-Artifacts 10, Security-Policy 9, Branch-Protection 8.
  - Weak: Maintained 1, Pinned-Dependencies 3 (in its own workflows), Token-Permissions 0, SAST 0.

## 5. Known vulnerabilities at 5.0.0
**None found on 2026-11-06.** The [GitHub Advisory Database](https://api.github.com/advisories?ecosystem=actions&affects=actions/upload-pages-artifact) and [repository advisories](https://api.github.com/repos/actions/upload-pages-artifact/security-advisories) both return `[]`. OSV's POST query was not available to me. The action has no npm dependencies of its own; the nested upload-artifact tree is covered in its own review.

## 6. License
MIT, "Copyright (c) 2022 GitHub, Inc." ([LICENSE](https://raw.githubusercontent.com/actions/upload-pages-artifact/fc324d3547104276b827a68afc52ff2a11cc49c9/LICENSE)).

## 7. Execution privileges
It is a `composite` action ([action.yml](https://raw.githubusercontent.com/actions/upload-pages-artifact/fc324d3547104276b827a68afc52ff2a11cc49c9/action.yml)). It runs in the build job as the runner user.
- **Inputs:**
  - `name` (default `github-pages`)
  - `path` (default `_site/`)
  - `retention-days` (default `1`)
  - `include-hidden-files` (default `false`)
  - The only output is `artifact_id`.
- **Step 1, shell.** It runs the runner's `tar` (`gtar` on macOS; `bash` and `--force-local` on Windows):
  - `tar --dereference --hard-dereference --directory "$INPUT_PATH" -cvf "$RUNNER_TEMP/artifact.tar" --exclude=.git --exclude=.github [--exclude=.[^/]*] .`
  - `path` is passed only as the env var `INPUT_PATH`. The one `${{ }}` inside `run:` evaluates to the fixed string `--exclude=.[^/]*` or to nothing, so there is no injection.
  - `--dereference` stores the content a symlink points to, and `--hard-dereference` does the same for hard links. **Files outside `path` can therefore enter the public artifact.**
  - `-v` lists every archived path in the job log.
  - There is no `chmod`; it was removed in v2.
- **Step 2, nested action.** `uses: actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f # v7.0.0` is called with these inputs:
  - `name: inputs.name`
  - `path: ${{ runner.temp }}/artifact.tar`
  - `retention-days`
  - `if-no-files-found: error`
  - That action uploads the tar to GitHub's artifact storage using the runner's runtime token (see its review).
- **Not used:** containers, the workflow token, writes outside `$RUNNER_TEMP`, and further network calls beyond the nested upload.

## 8. Direct dependencies as published
- **Actions it calls** (this is the whole list): `actions/upload-artifact@bbbca2ddaa5d8feaa63e36b76fdaad77386f024f`. That is tag `v7.0.0` ([ref](https://api.github.com/repos/actions/upload-artifact/git/ref/tags/v7.0.0)), which is **not** the latest. Latest is v7.0.2, `cf430e03…` ([latest](https://api.github.com/repos/actions/upload-artifact/releases/latest)). **Flagged: reviewed separately.**
- **Runner tooling:** GNU `tar` and `sh` from the runner image. These are covered by the runner-tooling inventory (`v01-design.md` §3), not by this review.

## 9. Alternatives considered
- **A `run:` tar step plus the separately reviewed upload-artifact 7.0.2.** We would own the archive format (`deploy-pages` expects a tar named `artifact.tar` inside an artifact named `github-pages`), but it removes the extra 7.0.0 pin.
- **upload-pages-artifact v4.0.0.** It is older, and its nested upload-artifact is v4.6.2.

## 10. Conditions and residual risk
The conditions are in the frontmatter. Residual risks:
- Whatever the build job writes into `path` is published, so the build job's own code (including Maven plugins) can alter the site. The dependency gate handles that, not this action.
- The `-v` file listing is public in the logs of a public repository.

## 11. Not verified
- The `tar` version on the runner image.
- Whether the repository's `sha_pinning_required` and allowed-actions list apply to actions nested in a composite. If they do, `upload-artifact@bbbca2dd…` must be in the allowed list or the job fails, which fails closed.
- The identity behind the web-flow signature.
- Whether an immutable action package exists for this release.
- The macOS and Windows branches, which are outside our scope.
