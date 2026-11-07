---
component: "Docker Engine (docker-ce) on GitHub-hosted ubuntu-24.04 runners (actions/runner-images)"
kind: runner-image
version: "28.0.4"
verdict: APPROVE-WITH-CONDITIONS
scope: "CI only: this repository's Testcontainers 2.0.5 PostgreSQL integration tests in a job with `runs-on: ubuntu-24.04` (GitHub-hosted, x64). Not `ubuntu-latest`, not `ubuntu-24.04-arm`, not larger or self-hosted runners, not any other Docker use (image builds, `services:`, `container:`, Docker actions). The rest of the runner image's software is not reviewed here."
conditions: ["Fail closed in code: the test base class aborts unless Engine ServerVersion is exactly 28.0.4 and the runc component is >= 1.3.3 (1.2.x rejected); it logs ServerVersion, ApiVersion, the containerd and runc versions and commits, KernelVersion and OperatingSystem", "`runs-on: ubuntu-24.04` exactly; the job log's `Set up job` > `Runner Image` version is kept with the run", "The first CI run's logged containerd and runc versions are appended to this review as an amendment before the job becomes a required check", "TESTCONTAINERS_RYUK_DISABLED=true; no privileged container", "Only the PostgreSQL 18.6 image from its own review, referenced by index digest; an image-set diff before and after the tests fails the job on any other new image (Ryuk, alpine, sshd)", "TESTCONTAINERS_CHECKS_DISABLE=true; no bind mounts, no copy-to-container (withCopyToContainer, withCopyFileToContainer, copyFileToContainer: the archive API), no exposeHostPorts/withAccessToHost, image builds, host networking or create-command modifiers", "A workflow step writes /etc/docker/daemon.json `{\"ip\":\"127.0.0.1\"}` and restarts docker, failing if the file already exists; the test asserts every published port has HostIp 127.0.0.1", "Explicit DOCKER_HOST=unix:///var/run/docker.sock, DOCKER_CONFIG pointing at an empty directory holding `{}`, no docker login, no testcontainers.properties on the classpath", "Job permissions `contents: read`, no secrets, no environment, checkout with persist-credentials false", "Any other Engine version (the runner-images toolset pin moving) is a delta review, not a relaxed check"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Docker Engine on GitHub-hosted `ubuntu-24.04`: CI scope for the Testcontainers tests

Extends `colima-review.md`, which approved Testcontainers on two local hosts and said "CI is not covered". Nothing was run on a runner for this review; every version below is from the published image README and toolset, and condition 3 closes the gap.

## 1. Verdict
**APPROVE-WITH-CONDITIONS** for Engine **28.0.4** on `ubuntu-24.04`, test phase only. The engine is old and unmaintained: the 28.x line ended at 28.5.2 on 2025-11-05, and seven 2026 Moby advisories affect 28.0.4 with fixes only in 29.x (section 5). The colima review **rejected** Engine 28.4.0 on the owner's MacBook for the same advisories. CI differs in three ways that make them unreachable or moot here: (a) each job gets a new VM that is discarded afterwards; (b) the job's own code already has passwordless root on that VM, so a container escape grants nothing the test code lacks, and the job holds no secrets and no write token (condition 9); (c) the only container is the digest-pinned, separately reviewed PostgreSQL image, and the advisories need a malicious image, container, registry, plugin or Swarm, or a `docker cp`/archive-API write the conditions forbid. Ryuk is **disabled**: on a VM that is destroyed after the job its cleanup role is moot, and it would add a privileged container holding the Docker socket, an unauthenticated port and an unreviewed image referenced by tag.

## 2. What it is and why it is needed
`v01-design.md` §4: PostgreSQL 18.6 with Testcontainers 2.0.5 for the example app's integration tests ("real transactions, migrations and isolated databases"; H2 rejected); §6 slices 5 and 10 need them in CI; §3 lists "PostgreSQL/Ryuk images, runner tooling" for inventory. Testcontainers needs a Docker-API engine; the runner's is the one already present.

## 3. Provenance
- Runner image: `actions/runner-images`, GitHub-owned. Current `ubuntu-24.04` README: "Image Version: 20261101.360.1", "OS Version: 24.04.5 LTS", "Kernel Version: 7.0.0-1017-azure", "Docker Client 28.0.4", "Docker Server 28.0.4", Compose 2.38.2, Buildx 0.38.1, Podman 4.9.3 (https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/Ubuntu2404-Readme.md). Tag `ubuntu24/20261101.360`, 2026-11-02, commit add4380 (https://github.com/actions/runner-images/tags).
- Install: `install-docker.sh` adds Docker's own apt repo `https://download.docker.com/linux/ubuntu` (`<codename> stable`), installs pinned packages as `package=version`, then deletes the repo file and key; plugins come from `docker/<plugin>` GitHub releases (https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/scripts/build/install-docker.sh).
- Pins in `toolset-2404.json`: `docker-ce` and `docker-ce-cli` `"28.0.4"`, compose `"2.38.2"`; **`containerd.io` and buildx `"latest"`**, so containerd and its bundled runc float with each image build (https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/toolsets/toolset-2404.json).
- Daemon config: the script writes **no daemon.json**; it renumbers the `docker` group and sets the socket to `root docker 0770` via tmpfiles. It pre-pulls six `ghcr.io/github/*` and `ghcr.io/dependabot/*` images by `:latest` (same script), which the tests must not run.
- Signing: apt verifies Docker's repo signature at image build time; the runner image itself has no published signature or attestation that this review found. SHA-256 of the packages: not published in anything fetched (`sha256: null`).

## 4. Maintenance and adoption
- Runner images: "We typically deploy weekly updates"; rollout "takes 2-3 days"; advance notice is promised only for default-version changes, two weeks ahead (https://github.com/actions/runner-images#readme). So the Engine can change between runs; condition 1 catches it.
- Announcement in the README: "`ubuntu-latest` label will use Ubuntu 26.04 in November 2026" (issue #14748), hence condition 2.
- Docker Engine: latest is 29.9.3 (2026-11-03); 28.5.2 (2025-11-05) is the last 28.x on the release-notes page, and the page states no EOL (https://docs.docker.com/engine/release-notes/28/, https://docs.docker.com/engine/release-notes/29/). The Go advisories below list patched "None" for the `github.com/docker/docker` <= v28.5.2 range.
- Isolation: "each GitHub-hosted runner is a new virtual machine (VM) hosted by GitHub" (single-CPU `ubuntu-slim` excepted); "passwordless `sudo`"; jobs may run on AWS or Azure (https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
- OpenSSF Scorecard: not checked (api.securityscorecards.dev unavailable to this job).

## 5. Known vulnerabilities at Engine 28.0.4
OSV `POST /v1/query` was not available; checked the Moby, runc and containerd GitHub advisory lists and the osv.dev list pages (https://github.com/moby/moby/security/advisories, https://osv.dev/list?q=github.com%2Fmoby%2Fmoby&ecosystem=Go).
| Advisory | Fixed in | Preconditions | Here |
|---|---|---|---|
| GHSA-7cfq-22r6-qp73 (High) | 29.8.2 | DNS attacker forces insecure-registry fallback; tag swap, credential capture | Mitigated: pull by digest, no credentials (condition 8) |
| GHSA-x86f-5xw2-fm2r (High) | 29.5.1 | Malicious image's xz/unpigz used when a compressed archive is piped into `docker cp -` or the archive API | Not reachable: trusted image, no copy-to-container |
| GHSA-rg2x-37c3-w2rh (High), GHSA-vp62-88p7-qqf5 (Mod) | 29.5.1 | Malicious container with a volume races symlinks during `docker cp`/archive API or mount creation | Not reachable: the PostgreSQL image has `VOLUME /var/lib/postgresql`, which is why copy-to-container is forbidden even though the image is trusted |
| GHSA-x744-4wpc-v9h2 (High), GHSA-pxq6-2prw-chj9 (Mod) | 29.3.1 | AuthZ plugins / a malicious plugin | Not reachable: no plugins |
| GHSA-6m9p-4h64-m6vh (Mod) | 29.8.2 | Swarm encrypted overlay | Not reachable |
- Not affected: GHSA-x4rx-4gw3-53p4 (28.2.0-28.3.2) and GHSA-4vq8-7jfc-9cvp (< 28.0.0).
- 29.x-only items not checked against 28.0.4: GHSA-x4jj-h2v8-hqqv (sparse-tar pull DoS), moby/go-archive GHSA-hfg8-hc9c-6c3h, moby/sys GHSA-mjcv-p78q-w5fw. All need a malicious image or archive. GHSA-6jhp-7446-hf7f returns 404.
- **runc** (bundled in `containerd.io`, version unknown): the container-escape set GHSA-9493-h29p-rfm2, -qw9x-cqr3-wc7r, -cgrx-mc8f-2prm is fixed in 1.2.8 / 1.3.3 / 1.4.0-rc.3; 1.2.x is "completely unsupported" per the 1.5.0 notes; GHSA-xjvp-4fhw-gc47 (Low) is "not exploitable under Docker" (https://github.com/opencontainers/runc/security/advisories). Hence the runc floor in condition 1.
- **containerd** (version unknown): advisories since 2024 need CRI (unused by dockerd), a hostile local user, or a malicious image or registry (https://github.com/containerd/containerd/security/advisories). Branches 1.7 and 2.1 are EOL; 2.2 ends 2026-11-06 (https://github.com/containerd/containerd/blob/main/RELEASES.md). Recorded, not gated (condition 3).
- docker CLI 28.0.4: GHSA-p436-gjf2-799p is Windows-only (https://github.com/docker/cli/security/advisories).

## 6. License
Docker Engine (moby) and docker CLI: Apache-2.0 (https://github.com/moby/moby, https://github.com/docker/cli). The `docker-ce` package licence and the runner image's own licence were not checked.

## 7. Execution privileges
- dockerd and containerd run as root on the runner VM. The socket is `root:docker 0770`; any socket client is root on the VM, which the job already is through sudo.
- Testcontainers 2.0.5 at startup calls Docker `info` and `version` and checks only "Docker server version should be at least 1.6.0" (`DockerClientFactory.java` at tag 2.0.5). With Ryuk on it would start `testcontainers/ryuk` privileged with the socket mounted; a lazy file-mount check (`checkMountableFile`) runs `alpine:3.17` with a bind mount (colima review §7; https://java.testcontainers.org/features/configuration/).
- PostgreSQLContainer: unprivileged, no binds, publishes 5432 with no HostIp, `postgres -c fsync=off`, default credentials `test`/`test`. Without a daemon.json, published ports listen on all VM interfaces (Docker's default `ip`); inbound reachability of runner VMs is not documented in what was read, hence condition 7.
- Network: image pulls from Docker Hub, anonymous. No telemetry found (colima review §7).

## 8. Direct dependencies as published
- `containerd.io` (latest at image build; bundles runc): the runtime under dockerd. Versions to be recorded (condition 3).
- `docker-ce-cli` 28.0.4: used only by the cleanup, image-diff and daemon steps.
- buildx (latest), compose 2.38.2, ECR credential helper: present but unused; any use needs its own review.
- The PostgreSQL 18.6 image: own review (`container-image--postgres--18.6-trixie.md`). Testcontainers 2.0.5 and its transitives: own Maven reviews.

## 9. Alternatives considered
- Engine 29.9.3 installed by the job: not reviewed, and an install step from download.docker.com is more third-party code. Asking runner-images for a newer pin is the better route.
- Podman 4.9.3 on the same image: unreviewed; Testcontainers' Podman path is less exercised.

## 10. Conditions and residual risk
Frontmatter conditions 1-10 apply. The engine check in CI behaves like the colima hosts' check, with an exact version, not the major: the approval is per version, and the toolset pins 28.0.4, so a change happens only when runner-images moves the pin, and then this check fails and a delta review is due. The runner passes the expected engine profile (`github-ubuntu-24.04`) to the tests, as the colima wrappers do. The diff of `docker image ls --digests` and the label sweep with `docker rm -f -v` still run in CI: the VM is discarded anyway, but the diff is the evidence that no unreviewed image ran.

**Residual risk.** The engine carries unfixed advisories, mitigated rather than fixed; a compromised PostgreSQL image would meet an engine that cannot be trusted to contain it, although on this VM it would gain only what the job already has. containerd and runc float weekly and are unreviewed until condition 3 is met. Test code, Gradle and plugins run with the job's own privileges; that boundary belongs to the controller design (`v01-design.md` §3), not to this review.

## 11. Not verified
- The containerd.io and runc versions actually on the image (the README lists neither); the release notes of `ubuntu24/20261101.360` (the releases API returned 403).
- Whether the `runner` user is in the `docker` group (the script sets the socket group but does not add the user); whether `~/.docker/config.json` exists on the image.
- Whether runner VMs accept inbound connections; Docker's default `ip` was not re-fetched from the dockerd reference.
- The ubuntu-24.04-arm image (same Docker 28.0.4 in its README, out of scope), Scorecard, and package SHA-256 values.
- That an EOL containerd branch is safe for this use: the reachability argument rests on the advisory texts, not on source reading.
