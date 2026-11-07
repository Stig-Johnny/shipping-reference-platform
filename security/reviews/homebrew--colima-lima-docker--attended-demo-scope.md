---
component: "Scope extension: colima, lima, the docker CLI and the colima-core VM engine, for the attended demo database, on the host the owner picks (PENDING): the owner's MacBook (profile demotest) or the Mac Mini (profile rigbuild)"
kind: homebrew
version: "As reviewed: colima 0.10.3, lima 2.2.0, docker 29.8.1; VM image colima-core v0.10.4 (Docker Engine 29.5.2). Any other installed version needs a delta review first"
verdict: APPROVE-WITH-CONDITIONS
scope: "Attended demo only. The owner starts the Java API's demo profile, whose PostgreSQL 18.6 database the reviewed harness ReviewedPostgres creates, and watches it for one session. The host is the owner's open decision (PENDING): variant A, the owner's MacBook in the colima profile demotest, or variant B, the Mac Mini's claude user in the colima profile rigbuild, reached over SSH. Only the picked variant is covered. Not for tests, AI agents, CI or any unattended use. Blocked until the harness, its launcher and the wrapper are reviewed and the picked host's precondition holds"
conditions: ["Host choice PENDING: the owner picks variant A (the owner's MacBook, colima profile demotest) or variant B (the Mac Mini, user claude, colima profile rigbuild, over SSH). Only the picked variant is covered, recorded as an amendment of this record; nothing runs before that", "Blocked until reviewed: the harness ReviewedPostgres (pull request #54) with an explicit engine case for the picked profile (colima-demotest or colima-rigbuild, ServerVersion 29.5.2, linux/arm64; at #54's head 9464cf3 it enumerates only the GitHub-hosted runner), the test-classpath launcher that owns ReviewedPostgres.Database, and tools/demo/run-attended-demo.sh", "The database container is created only by ReviewedPostgres inside the launcher's JVM. The script runs no docker run, create or start, and PolicyCheck's rule for docker commands in scripts (check 8, pull request #54) stays as it is", "The harness generates the database password. The launcher hands the JDBC URL and the credentials to the application in-process and never logs, prints, exports or writes them; the script neither generates nor sees a password", "Operated by the owner, attended, in the foreground: started from the owner's own interactive terminal (variant B: from an SSH session opened there) and stopped by the owner when the session ends. Never started by an AI agent, a scheduler, launchd, CI or any process that outlives the terminal or the SSH session", "The exact revision whose CI checks are green, with its web assets, migrations and synthetic seed data; Gradle runs with --no-daemon", "Engine: every condition of the picked profile's entries in homebrew--colima-lima-docker--scope-extension.md: vz, zero host mounts, docker.ip and host_binding_ipv4 127.0.0.1, an explicit DOCKER_HOST, an isolated DOCKER_CONFIG containing {}, no credential helper, and no build, buildx, compose, login, plugin or Swarm. The reviewed PostgreSQL digest is the only image; the harness asserts the engine's Name and ServerVersion 29.5.2", "Network: the application listens on 127.0.0.1 only, and the database port is published on 127.0.0.1 only and never forwarded. Variant B forwards only the application's HTTP port, with ssh -N -o ExitOnForwardFailure=yes -o ForwardAgent=no -L 127.0.0.1:8080:127.0.0.1:8080", "Lifetime: one session. Closing the application closes the Database, which removes the container with its volume. On exit, error, interrupt or a dropped SSH session, the script sweeps the session's labelled containers, volumes and networks while the engine is still reachable, checks that none remain, then stops the profile; after an abrupt failure the owner reconnects and checks. Afterwards lsof shows no listener off loopback", "Variant A is blocked until the MacBook matches. On 2026-11-07 it has colima 0.9.1, lima 2.0.2 and docker 29.1.2, none pinned, so it needs colima 0.10.3 (arm64_tahoe bottle a9dfd1fa0a4aee62fef75974f39f174e4da774f7ba495c43dd0bcc23633381b8) installed and pinned, and a delta review of the lima and docker CLI versions actually installed (homebrew-core serves lima 2.3.0 and docker 29.9.3). The owner agrees to lima's override.yaml first, because it affects every lima instance; the running instance (Engine 28.4.0) stays REJECTED", "Variant B: before each session the orchestrator checks that the Mac Mini has colima 0.10.3, lima 2.2.0 and docker 29.8.1 with the reviewed bottles, pinned, engine colima-rigbuild with ServerVersion 29.5.2, zero mounts, loopback forwarding and the isolated docker configuration, and stops on any mismatch, without upgrading. Runs go through ~/bin/with-rigbuild.sh once its amendment is recorded (the reviewed image list with only the PostgreSQL 18.6 digest, -v and the network sweep). The rigbuild profile is reserved for the demo during rehearsal and demo, with no other work on it", "The first attended run on the picked host is recorded as an amendment of this record"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-07"
expiresAt: "2027-05-06"
---

# Scope extension: colima, lima and docker for the attended demo database

**Not a new review.** This record extends `homebrew--colima-lima-docker--scope-extension.md` ("the base record"),
which approves these exact versions for two hosts:

- the Mac Mini's `claude` user, in the profile `rigbuild`, through `~/bin/with-rigbuild.sh`;
- the owner's MacBook, in a new profile `demotest`.

Both are for one use: Testcontainers integration tests. The base record's provenance, maintenance, advisory and
licence findings stand. Its host conditions all still apply, and so does its rejection of the MacBook's running colima
instance (Docker Engine 28.4.0).

What is new is one attended use: the database for the API's `demo` profile, created by the reviewed harness, with its
lifetime and its host. The host is the owner's open decision, between the two variants below.

**Method.** On 2026-11-07:

- **Re-read** the base record, the PostgreSQL 18.6-trixie record and the harness `ReviewedPostgres` at pull request
  #54's head `9464cf3`.
- **Read the MacBook's installed state** from the file system, without running `brew`, colima or docker: `sw_vers`,
  `uname -m`, the Homebrew Cellar's version directories and their `INSTALL_RECEIPT.json` files, the pin directory and
  the names of the colima profile directories.
- **Read** the current formulae from `https://formulae.brew.sh/api/formula/{colima,lima,docker}.json`.
- **Not read:** the Mac Mini.

Variant B rests on the base record and on the independent review of pull request #73, which recommends it.

## 1. Verdict

**APPROVE-WITH-CONDITIONS, for the variant the owner picks, and blocked today.**

- **The demo use adds little risk.** It is the base record's engine with one container, which the reviewed harness
  creates and removes, for one session that the owner watches.
- **Two things block it now.** The harness, its launcher and the wrapper do not exist in reviewed form yet (condition
  2). The MacBook does not have the reviewed versions (variant A, section 4). Variant B's host is not checked by this
  record (condition 11).

| Variant | Host and profile | Status on 2026-11-07 |
|---|---|---|
| A | The owner's MacBook, the owner's own account, colima profile `demotest`, run from the owner's terminal | Blocked: the installed colima, lima and docker are not the reviewed versions |
| B | The Mac Mini, user `claude`, colima profile `rigbuild`, through `~/bin/with-rigbuild.sh`, from an SSH session; the MacBook's browser reaches the application through an SSH tunnel | Recommended by the independent review of pull request #73; host checks pending (condition 11) |

**The owner's pick: PENDING.**

## 2. What it is and why it is needed

The reviewed plan runs an attended demonstration of the same revision that CI tests: the Java API in its `demo`
profile, backed by a real PostgreSQL database with its migrations, and a browser that the owner operates. The base
record approves the engines only for Testcontainers tests. This record approves the one container the demo needs, on
one of the two hosts.

How it is started:

- **The container comes from the harness.** It is created only by `ReviewedPostgres`, the reviewed Testcontainers
  harness, inside a JVM started by a test-classpath launcher. That launcher owns the harness's `Database` and runs the
  application on `127.0.0.1:8080`.
- **The repository's entry point is a fail-closed script,** `tools/demo/run-attended-demo.sh`, which another pull
  request writes. This record names its path.
- **On the Mac Mini** the script runs inside the existing wrapper `~/bin/with-rigbuild.sh`.
- **No `docker run`.** The script runs no `docker run`. PolicyCheck's rule 8, as pull request #54 proposes it, refuses
  that in any shell script, and this record does not ask for an exception.

## 3. Provenance

Unchanged from the base record (section 3): the colima, lima and docker bottles by SHA-256, and the colima-core v0.10.4
VM image by the SHA-512 compiled into colima 0.10.3. On 2026-11-07 homebrew-core's colima formula is still 0.10.3,
with the reviewed `arm64_tahoe` bottle `a9dfd1fa0a4aee62fef75974f39f174e4da774f7ba495c43dd0bcc23633381b8`.

## 4. Maintenance and adoption: the hosts today

**Variant A, the MacBook.**

| Item | Reviewed | On the MacBook (2026-11-07) | homebrew-core today |
|---|---|---|---|
| macOS | 26 (tahoe) or 15 | 26.5 (build 25F71), arm64 | — |
| colima | 0.10.3, pinned | **0.9.1**, not pinned | 0.10.3 (bottle as reviewed) |
| lima | 2.2.0, pinned | **2.0.2**, not pinned | **2.3.0** (arm64_tahoe `31d56468…092f`), unreviewed |
| docker CLI | 29.8.1, pinned | **29.1.2**, not pinned | **29.9.3** (arm64_tahoe `42aa841a…4f67`), unreviewed |
| colima profiles | `demotest` (new) | only `default` (the rejected running instance) | — |

- **How the three were installed.** All three were poured from bottles on 2025-12-07 (receipt `time` 1765131921).
- **colima.** Version 0.9.1 would create a new profile from a colima-core 0.9.x image. The base record's Engine 28.4.0
  came from that line, and it is rejected for unfixed runc container escapes. So colima must be 0.10.3.
- **lima and the docker CLI.** Neither 2.2.0 nor 29.8.1 can be installed fresh: homebrew-core serves only its current
  versions. That leaves two ways forward:
  - a delta review of lima 2.3.0 and docker 29.9.3. The base record notes that 2.3.0 fixes the two lima advisories of
    2026-11-06;
  - or a delta review of the installed 2.0.2 and 29.1.2. lima 2.0.2 is inside GHSA-2j9v-p4xj-cjw2 (High, `<= 2.1.2`,
    fixed in 2.1.3; its summary names QEMU VMs), which the base record ruled out only for 2.2.0.
- **Side effects.** Replacing the MacBook's shared colima and lima can affect the owner's existing instance, and lima's
  `override.yaml` affects every instance.

**Variant B, the Mac Mini.**

- **Already reviewed, not yet checked.** The base record already approves colima 0.10.3, lima 2.2.0 and docker 29.8.1
  for its `claude` user and the `rigbuild` profile, through `~/bin/with-rigbuild.sh`, with Engine 29.5.2. This record
  did not check the Mini's installed state. Condition 11 has the orchestrator check it before each session, and stop
  on any mismatch.
- **One wrapper change is still unrecorded.** The base record also asks for a `with-rigbuild.sh` change: the reviewed
  image list with only the PostgreSQL 18.6 digest, `-v` and the network sweep, recorded as an amendment. That amendment
  is not in this repository yet.

## 5. Known vulnerabilities at these versions

Unchanged from the base record for the reviewed versions: Engine 29.5.2's later advisories are mitigated, not fixed,
by digest-only pulls, no credentials, no builds, no Swarm and no plugins. The MacBook's installed versions in section
4 were not assessed here; that is the delta review condition 10 asks for.

## 6. License

Unchanged: colima MIT, lima Apache-2.0, docker CLI Apache-2.0, Docker Engine Apache-2.0.

## 7. Execution privileges

- **Both variants run what the tests already run.**
  - colima and limactl run as the host user, and dockerd runs as root in the VM.
  - The harness's container publishes one port, on host loopback only (`docker.ip` and `host_binding_ipv4`
    127.0.0.1). The harness asserts that on every published port.
- **What is new.** A person, not a test fixture, starts the session. The container lives for the session instead of a
  test run, and the application connects to it from the same host.
- **The password stays in the JVM.** The harness generates it (`SecureRandom`, `ReviewedPostgres.java:381` at
  `9464cf3`). The launcher passes it to the application in-process, so it never enters the environment, a file or the
  script.
- **Who can reach the ports.** Loopback leaves both ports reachable by every process of the host's user and by other
  local users, for the session only. The database holds synthetic data only, and the application is its one client.
- **Variant B adds an SSH tunnel.** It runs from the owner's MacBook to the Mac Mini and forwards one port, the
  application's HTTP, to the MacBook's loopback, with agent forwarding off. It also adds a shared host:
  - passwordless `sudo` on the Mini makes its wrapper a speed bump, not a boundary (base record);
  - so the profile is reserved for the demo during rehearsal and demo.
- **Not a sandbox from the user.** The VM has no host mounts, so containers cannot write the host user's home
  directory. That does not isolate the session from other software running as that user.

## 8. Direct dependencies as published

Unchanged: colima depends on lima; the VM image has its own entry. The PostgreSQL image has its own record and an
attended-demo scope extension, and the harness's Testcontainers and docker-java have their own reviews.

## 9. Alternatives considered

- **A recorded CI run.** Screenshots, a trace and test evidence of the real application against PostgreSQL in CI. It
  needs no local engine at all, and the independent review names it as the fallback if neither variant is approved and
  rehearsed in time. Media wait for the repository's media gate.
- **A native PostgreSQL on the MacBook.** Another package and lifecycle to review.
- **`docker run` from the script.** Rejected: it needs an exception to rule 8, and it would put the password in the
  script.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter. Conditions 1 and 2 block everything; 10 or 11 applies to the picked host.

**Residual risk.**

- **The host user's own account.** The demo runs as the host's user, next to that user's credentials: the owner's on
  the MacBook, the build user's on the Mini. The engine's later advisories are mitigated, not fixed.
- **Loopback is shared.** A port bound to 127.0.0.1 is reachable by every local process for the session.
- **A missed cleanup.** A failed cleanup could leave a container and its volume behind. The checks after the session,
  and after a reconnect, are what catch that.

## 11. Not verified

- **The Mac Mini's installed state, its pins and its wrapper.** Condition 11 checks them before each session.
- **The MacBook after an update:** versions, bottle hashes and pins. That is condition 10's record.
- **The harness's engine case, the launcher and the script,** which do not exist in reviewed form yet.
- **The running MacBook instance:** its colima and lima versions and its mount and port configuration. Its profile
  directory `default` exists; its configuration was not read.
- **The delta review** of whichever lima and docker CLI versions variant A would use.
