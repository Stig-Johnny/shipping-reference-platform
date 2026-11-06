---
component: "homebrew-core colima + lima + docker (CLI), and the Docker Engine inside the colima VM"
kind: homebrew
version: "colima 0.10.3, lima 2.2.0, docker 29.8.1; VM image colima-core v0.10.4 (Docker Engine 29.5.2)"
verdict: APPROVE-WITH-CONDITIONS
scope: "Test phase only: this repository's Testcontainers integration tests on (1) the Mac Mini `claude` user, only through ~/bin/with-rigbuild.sh (profile rigbuild); (2) the owner's MacBook, only in a NEW colima profile `demotest` after the host checks in section 10 pass. The MacBook's running colima instance (Docker Engine 28.4.0) is REJECTED. CI is not covered."
conditions: ["Exact versions only: colima 0.10.3, lima 2.2.0, docker 29.8.1 with the bottle sha256 in section 3, `brew pin`ned; no `brew upgrade` (lima 2.2.1/2.3.0 and docker 29.9.3 are unreviewed)", "The test fixture fails closed unless the engine reports Name colima-rigbuild or colima-demotest and ServerVersion 29.5.2", "TESTCONTAINERS_RYUK_DISABLED=true on both hosts; no privileged container at all", "Only images that have their own recorded container-image review (PostgreSQL 18.6), pulled by digest and retagged locally; no other image in the VM", "TESTCONTAINERS_CHECKS_DISABLE=true; no bind mounts, exposeHostPorts/withAccessToHost, image builds, host networking or create-command modifiers", "VM: zero host mounts (mounts: null), docker.ip 127.0.0.1 + host_binding_ipv4 127.0.0.1, lima override.yaml ignoring guest ports 1-1023, lsof listener check after every run", "Explicit DOCKER_HOST, isolated DOCKER_CONFIG ({}), no ~/.testcontainers.properties, no classpath testcontainers.properties", "Cleanup before and after each run: label sweep with `docker rm -f -v` plus network removal, then `colima stop`; Gradle runs with --no-daemon", "MacBook: only after the orchestrator records the checks in section 10; any version mismatch means a delta review first"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# colima / lima / docker: scope extension for this repository's Testcontainers tests

Extends the 2026-09-24 review (amended 2026-10-20; "base review"), which approved these formulae for one scope only: another repository's delegated build/test on the Mac Mini `claude` user in the `rigbuild` VM. Nothing was installed, started or read on either host for this review. Every host fact in section 10 is for the orchestrator to verify.

## 1. Verdict
| Host / engine | Decision |
|---|---|
| Mac Mini `claude`, via `with-rigbuild.sh`, profile `rigbuild`, Engine 29.5.2 | **APPROVE-WITH-CONDITIONS**. Same artifacts, same wrapper. Ryuk stays disabled. The wrapper's image list gains only the reviewed PostgreSQL 18.6 digest |
| Owner's MacBook, the running colima instance, Engine 28.4.0 | **REJECT**. The engine has unfixed runc container-escape and `docker cp` advisories, and its colima/lima versions and mount/port config are unknown |
| Owner's MacBook, new profile `demotest`, Engine 29.5.2 | **APPROVE-WITH-CONDITIONS**, only if the MacBook has exactly the reviewed versions and bottles (section 10). Otherwise a delta review comes first |

Ryuk is **not** approved on either host. Its only job is cleanup after a `kill -9`, and a label sweep does that. In return it would need a privileged container holding the Docker socket and an unauthenticated port (section 7).

## 2. What it is and why it is needed
colima runs a Linux VM through lima (Apple Virtualization.Framework, `vz`) with dockerd inside. The docker CLI reaches it over a forwarded unix socket. The integration tests use Testcontainers for Java to start PostgreSQL 18.6; the brief also names Ryuk. **`v01-design.md` was not in the inputs**, so the Testcontainers version, build tool and image tag are not confirmed. Section 7 assumes the latest release, **2.0.5** (https://github.com/testcontainers/testcontainers-java/releases).

## 3. Provenance (unchanged from the base review; hashes re-fetched today)
Sources: colima `github.com/abiosoft/colima` v0.10.3 rev `00f6c297e92a82c04a4ab507db0a61435650d7e8`; lima `github.com/lima-vm/lima` (CNCF) `v2.2.0.tar.gz` sha256 `cdba3804df7d8c00a2af674a3fe0b24c19673a0e846e5f75ac9badf227ce52f5`; docker CLI `github.com/docker/cli` v29.8.1 rev `4a63305d74332de5ceba7fcbccbc3cbb7412f5ba`. Homebrew CI builds all three from source, and no upstream signature exists. Bottle SHA-256s as published in the formula `bottle do` blocks (macOS 26 tahoe / macOS 15 sequoia):
- colima `a9dfd1fa0a4aee62fef75974f39f174e4da774f7ba495c43dd0bcc23633381b8` / `56ea218ab2b408e940c4d690a547c082b5be3cd3cf581ddbfe0f3a0166434ae1` (https://github.com/Homebrew/homebrew-core/blob/68c6685c8ba3636769fbc01229d1f9a01923ccb6/Formula/c/colima.rb; still current in https://formulae.brew.sh/api/formula/colima.json)
- lima `9dfb60d4c7d0c6721eee679ab188d8b502ad61d1063c9914b830b66ce795b4a4` / `bf9f97484c9dc02cfd4a0a9dacf6c890652f2b2fb324410db0ca0acaacd326dc` (https://github.com/Homebrew/homebrew-core/blob/244eee4af96157251c0284d83a8f87efef3dc442/Formula/l/lima.rb)
- docker `40d9396a0e2a2edfea5c69137b0d5722f2049da0a218a3e1b1c94080c5f1b3f0` / `0c0a2c666769a8f29ebe8036f5b39d9a7868f6a76ea3cce1db1ce28bab8cd43b` (bottle commit https://github.com/Homebrew/homebrew-core/blob/559853ee927265109cf541c85cd2be4addd16d19/Formula/d/docker.rb; the bump commit before it, 2b6fe1b, still carries the 29.8.0 block)

All three tahoe values match the base review.

VM image: colima 0.10.3's `images.txt` pins colima-core v0.10.4 `ubuntu-24.04-minimal-cloudimg-arm64-docker.raw.gz` with sha512 `32242674…40abbd`. The pin is unchanged at main (https://github.com/abiosoft/colima/blob/v0.10.3/embedded/images/images.txt). The image is unsigned and has no attestation. Its build pins `DOCKER_VERSION: "29.5.2"` (https://github.com/abiosoft/colima-core/blob/v0.10.4/.github/workflows/release.yml).

## 4. Maintenance and adoption: reviewed vs current (2026-11-06)
| Formula | Reviewed | Current | Source |
|---|---|---|---|
| colima | 0.10.3 | 0.10.3, still the latest release (2026-06-04) | https://github.com/abiosoft/colima/releases |
| lima | 2.2.0 | 2.2.1 (2026-10-03) in the API; **2.3.0 (2026-11-06)** in the tap | https://github.com/lima-vm/lima/releases, https://github.com/Homebrew/homebrew-core/commits/main/Formula/l/lima.rb |
| docker | 29.8.1 | 29.9.3 (2026-11-03) | https://github.com/Homebrew/homebrew-core/commits/main/Formula/d/docker.rb |
| Engine in VM | 29.5.2 | 29.9.3 (2026-11-03). colima has shipped no newer image since 2026-06 | https://github.com/moby/moby/releases |

`brew install` serves only the current formula, so **lima 2.2.0 and docker 29.8.1 can no longer be installed fresh**. A host without them needs a delta review of lima 2.3.0 and docker 29.9.3, not an unreviewed upgrade. OpenSSF Scorecard was not checked.

## 5. Known vulnerabilities at these versions
OSV `POST /v1/query` was not available to this job. The checks used the osv.dev list pages, `GET /v1/vulns/<id>` and the GitHub advisory pages.
- **colima 0.10.3:** none found on 2026-11-06 (https://github.com/abiosoft/colima/security/advisories, https://osv.dev/list?q=github.com%2Fabiosoft%2Fcolima&ecosystem=Go).
- **lima 2.2.0:** two advisories were published 2026-11-06, both fixed in 2.3.0 and not yet in OSV. Neither is reachable here: GHSA-566x-598w-h7w7 (Moderate) needs the QEMU driver, not `vz`, and GHSA-4wvf-9pjc-cpfp (Low) needs `limactl shell --sync`, which colima does not use. GHSA-2j9v-p4xj-cjw2 (≤2.1.2) and CVE-2023-32684 do not apply (https://github.com/lima-vm/lima/security/advisories).
- **docker CLI 29.8.1:** none found. GHSA-p436-gjf2-799p affects <29.2.0 and Windows only; GHSA-99pg-grm5-qq3v dates from 2021 (https://github.com/docker/cli/security/advisories).
- **Engine 29.5.2** (Mini and `demotest`) is affected by advisories fixed in later releases (https://docs.docker.com/engine/release-notes/29/):
  - GHSA-7cfq-22r6-qp73 (High, <29.8.2): a DNS attacker forces an insecure-registry fallback, then swaps tags and captures credentials. **Mitigated:** images are pulled by digest only, the advisory's own defence, and the isolated `DOCKER_CONFIG` holds no credentials.
  - GHSA-hfg8-hc9c-6c3h (High, fixed 29.7.0): a tar archive can write outside its directory. It needs an attacker-controlled archive, and CLI 29.8.1 already has the fix.
  - Denial of service that needs a malicious image or registry: GHSA-mjcv-p78q-w5fw, -jpcc-p29g-p8mq, -vwhp-8p66-mq4f, -8647-2jxj-p2rc, -hc94-p972-ch66, -mgcc-cww5-fp2q, -fpmh-frq8-7rj8.
  - Not reachable: BuildKit (GHSA-7236-3392-c5c6, -72x6-4j93-7w86 and the 29.8.2 set), because no image builds are allowed; and Swarm (GHSA-6m9p-4h64-m6vh). GHSA-6jhp-7446-hf7f could not be fetched.
- **Engine 28.4.0** (the MacBook's running instance) has all of the above, plus **runc container escapes** GHSA-9493-h29p-rfm2, -qw9x-cqr3-wc7r, -cgrx-mc8f-2prm (fixed in 28.5.2), **`docker cp` / archive API** GHSA-rg2x-37c3-w2rh, -vp62-88p7-qqf5, -x86f-5xw2-fm2r (High, fixed only in 29.5.1; the 28.x line ended at 28.5.2 on 2025-11-05 with no fix), and the plugin advisories GHSA-pxq6-2prw-chj9 and -x744-4wpc-v9h2 (https://github.com/moby/moby/security/advisories, https://github.com/opencontainers/runc/security/advisories, https://docs.docker.com/engine/release-notes/28/).
  In these advisories "host" means the VM. **If that VM mounts `~` writable, which is colima's default (base review §2.1), VM root reaches the owner's home directory.**

## 6. License
colima MIT, lima Apache-2.0, docker CLI Apache-2.0 (formula JSON `license`), Docker Engine (moby) Apache-2.0.

## 7. Execution privileges (what the tests make the engine do; Testcontainers Java 2.0.5 source)
- **Engine:** colima and limactl run as the host user, forward guest ports and the socket, and write `~/.colima` and `~/.lima`. dockerd runs as root in the VM, so **any socket client is root in the VM**. With zero mounts, the VM reaches the host only through lima's gateway to host loopback (base review §2.4).
- **Ryuk** (`RyukContainer.java`) is `testcontainers/ryuk:0.14.0`, referenced by tag, not digest. It is **privileged by default** (`isRyukPrivileged()` defaults to `"true"`), binds the Docker socket (from `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` or the `DOCKER_HOST` path), and publishes 8080 with no `HostIp`, accepting kill filters without authentication. Only the env var `TESTCONTAINERS_RYUK_DISABLED` disables it; cleanup then falls back to a JVM shutdown hook (https://github.com/testcontainers/testcontainers-java/tree/2.0.5/core/src/main/java/org/testcontainers/utility).
- **PostgreSQLContainer:** not privileged, no binds; runs `postgres -c fsync=off` with user and password `test`/`test`. It publishes 5432 with **no `HostIp`**, so the port stays on loopback only because of `docker.ip: 127.0.0.1` (`PostgreSQLContainer.java`, `ContainerDef.applyTo`).
- **Implicit pulls:** `alpine:3.17` (`DockerClientFactory.TINY_IMAGE`) when a container has binds and checks are on; `testcontainers/sshd` for `exposeHostPorts`, which logs in as root and accepts any host key (`PortForwardingContainer.java`). Otherwise the default pull policy pulls only images missing locally.
- **Engine selection** (`DockerClientProviderStrategy.java`): `tc.host` (user file), then `DOCKER_HOST`, then the persisted strategy, then `/var/run/docker.sock`, then the Docker Desktop sockets. **Docker contexts are not read.** A dead `DOCKER_HOST` falls through with only debug or warn logs. If the unix-socket strategy wins, Testcontainers **writes `docker.client.strategy` to `~/.testcontainers.properties`**, and the Mini wrapper then refuses every later run (exit 98) (https://java.testcontainers.org/supported_docker_environment/, https://java.testcontainers.org/features/configuration/).
- **Network:** only the Docker API is contacted (`User-Agent: tc-java/<v>`, `x-tc-sid`). No telemetry endpoint was found in the files read.

## 8. Direct dependencies as published
- colima depends on lima. lima has none on macOS (qemu and `lima-additional-guestagents` are not approved). docker has none.
- Runtime download: the colima-core VM image, unsigned, which **has its own allowlist entry**. The PostgreSQL 18.6 image needs its own container-image review; this review approves the engine, not images.

## 9. Alternatives considered
- Docker Desktop and OrbStack: both unreviewed, and OrbStack caused the 2026-10-15 fall-through (base review amendment).
- Integration tests in CI only: no local engine risk at all. This is the recommended default for agent runs on the MacBook.

## 10. Conditions, host checks and residual risk

**Both hosts**
1. `TESTCONTAINERS_RYUK_DISABLED=true`, and no privileged containers.
2. Images by digest only, from their reviews: `docker pull <repo>@sha256:…`, then `docker tag`. Check `RepoDigests` before each run. No other image in the VM.
3. `TESTCONTAINERS_CHECKS_DISABLE=true`. The tests use none of: host binds (`withFileSystemBind`; use `withCopyToContainer`), `exposeHostPorts`/`withAccessToHost`, `ImageFromDockerfile` or any build, `withPrivilegedMode`, host networking, `withCreateContainerCmdModifier`.
4. Environment: explicit `DOCKER_HOST`; an isolated `DOCKER_CONFIG` containing `{}`; no `~/.testcontainers.properties` and no `testcontainers.properties` on the test classpath; `TESTCONTAINERS_DOCKER_CLIENT_STRATEGY` and `TESTCONTAINERS_HOST_OVERRIDE` unset.
5. **Fail closed in code:** a test base class reads `DockerClientFactory.instance().client().infoCmd().exec()` and aborts unless `Name` is the expected profile host and `ServerVersion` is `29.5.2`. The runner passes in the expected host: `colima-rigbuild` or `colima-demotest`.
6. Gradle runs with `--no-daemon`, because a daemon outlives the run and keeps its own environment. The JDK and the build tool need their own reviews.
7. Before and after each run: `docker rm -f -v $(docker ps -aq --filter label=org.testcontainers=true)` (`-v` also removes anonymous volumes), then `docker network rm $(docker network ls -q --filter label=org.testcontainers=true)`; at the end, `colima stop --profile <p>`.

**Mac Mini.** Every base-review condition still applies. Change `with-rigbuild.sh` from one pinned image to a reviewed list (adding only the PostgreSQL 18.6 digest), add `-v` and the network sweep, and record the change as an amendment. The wrapper's "image set unchanged" FINDING (exit 96) already catches an implicit Ryuk, alpine or sshd pull.

The orchestrator verifies: `brew list --versions colima lima docker` shows 0.10.3 / 2.2.0 / 29.8.1 and `brew list --pinned` lists all three. Inside the wrapper, `colima version` shows 0.10.3 at commit `00f6c297…`, `limactl --version` shows 2.2.0, `docker version --format '{{.Client.Version}} {{.Server.Version}}'` prints `29.8.1 29.5.2`, and `docker info --format '{{.Name}}'` prints `colima-rigbuild`.

**MacBook (`demotest`).** Never point the tests at the running instance.
1. Run `sw_vers` and `brew list --versions colima lima docker`. Compare the bottle hashes (receipts, Homebrew cache) with the tahoe or sequoia values in section 3; any other macOS is not covered. **On any mismatch, stop: the next step is a delta review, not an upgrade.**
2. Create `~/.colima/demotest/colima.yaml` from the base review's profile: `vmType: vz`; `mounts: null`, started with `--mount none`; `docker.ip` and `host_binding_ipv4` set to `127.0.0.1`; `sshConfig: false`, `forwardAgent: false` and `autoActivate: false` (which keeps the owner's current docker context); no Rosetta, no Kubernetes.
3. lima's `override.yaml` affects **every** lima instance, including the owner's existing one, so the owner agrees to it first.
4. After start, `docker version` must show server 29.5.2 and the instance `lima.yaml` must have no mounts. After every run, `/usr/sbin/lsof -nP -iTCP -sTCP:LISTEN` must show no `limactl` or `ssh` listener off loopback.
5. Runs go through a fail-closed script that applies conditions 2, 4 and 7, a MacBook counterpart of `with-rigbuild.sh`. Until that script exists, runs are attended only. Record the first run as an acceptance amendment.

**Residual risk.** Test code, Gradle and its plugins run as the host user, next to its credentials; on the MacBook that is the owner's own account. Containers can reach host-loopback services. The VM image is unsigned. Engine 29.5.2 is behind the current release, so its later advisories are mitigated, not fixed. Passwordless sudo on the Mini makes the wrapper a speed bump, not a boundary.

## 11. Not verified
- **Host state:** installed versions, receipts and profile configs; which colima and lima drive the MacBook's 28.4.0 instance; whether that instance mounts `~` or forwards ports to `0.0.0.0`. Engine 28.4.0 only shows that the VM disk came from colima-core v0.9.0-2 (colima 0.9.0/0.9.1); colima never upgrades an existing VM's engine.
- **`v01-design.md` (missing):** the Testcontainers version, the build tool, and whether the repo ships `testcontainers.properties`.
- **Sources not queried:** OSV `POST /v1/query`, OpenSSF Scorecard.
- **Advisory gaps:** GHSA-6jhp-7446-hf7f, the range of GHSA-99pg, Go stdlib CVEs in Engine 29.5.2, the runc version in colima-core v0.10.4, and what lima 2.2.1 fixed.
- **Testcontainers behaviour:** `JVMHookResourceReaper`; whether Testcontainers honours `DOCKER_CONFIG` for registry auth; whether Ryuk works unprivileged on colima (moot while it is disabled).
