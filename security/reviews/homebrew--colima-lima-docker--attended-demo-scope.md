---
component: "Scope extension: colima, lima, the docker CLI and the colima-core VM engine, for the attended demo database on the owner's MacBook"
kind: homebrew
version: "As reviewed: colima 0.10.3, lima 2.2.0, docker 29.8.1; VM image colima-core v0.10.4 (Docker Engine 29.5.2). Any other installed version needs a delta review first"
verdict: APPROVE-WITH-CONDITIONS
scope: "Attended demo only: the owner, at the owner's MacBook, starts one PostgreSQL 18.6 container for the API's demo profile in the colima profile demotest, through tools/demo/run-attended-demo.sh, watches it for one demo session and removes it afterwards. Not for tests, AI agents, CI or any unattended use. Blocked until the host precondition (condition 1) is met"
conditions: ["Blocked until the host matches: on 2026-11-07 the MacBook has colima 0.9.1, lima 2.0.2 and docker 29.1.2, none pinned; nothing here may run until colima 0.10.3 (arm64_tahoe bottle a9dfd1fa0a4aee62fef75974f39f174e4da774f7ba495c43dd0bcc23633381b8) is installed and pinned and the lima and docker CLI versions actually installed have a recorded delta review (homebrew-core serves lima 2.3.0 and docker 29.9.3 on 2026-11-07); then the orchestrator records sw_vers, brew list --versions, the bottle hashes and brew list --pinned", "Operated by the owner, attended: started from the owner's own interactive terminal and stopped by the owner when the session ends; never started by an AI agent, a scheduler, launchd, CI or any process that outlives the terminal", "Started only by tools/demo/run-attended-demo.sh (written by another pull request, CODEOWNERS-protected), which fails closed: it starts nothing unless every check here passes", "Engine: only the colima profile demotest with every condition of the demotest entries (vmType vz, mounts none, docker.ip and host_binding_ipv4 127.0.0.1, sshConfig, forwardAgent and autoActivate false); the script asserts engine Name colima-demotest and ServerVersion 29.5.2; the owner's running instance (Engine 28.4.0) is REJECTED and never used", "Client: explicit DOCKER_HOST=unix://$HOME/.colima/demotest/docker.sock, an isolated DOCKER_CONFIG containing {}, no credential helper on PATH; no docker build, buildx, compose, login, plugin or Swarm", "Images: only the reviewed PostgreSQL index digest, pulled by digest; the VM holds no other image before the session and the same image set after it (container-image--postgres--18.6-trixie--attended-demo-scope.md)", "One container, labelled for the sweep; not privileged, no bind mount, no host network, no added capability, nothing copied into it; its one port published on 127.0.0.1 only, asserted with docker inspect before the API connects", "Lifetime: one demo session. On exit, error or interrupt the script removes the container with its anonymous volume (docker rm -f -v), removes labelled volumes and networks, and runs colima stop --profile demotest; afterwards docker ps -a and docker volume ls show nothing labelled, and lsof shows no listener off loopback", "lima's override.yaml (guest ports 1-1023 ignored) only with the owner's agreement, because it affects every lima instance", "The first attended run is recorded as an amendment of this record"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-07"
expiresAt: "2027-05-06"
---

# Scope extension: colima, lima and docker for the attended demo database

**Not a new review.** This record extends `homebrew--colima-lima-docker--scope-extension.md` ("the base record"),
which approves these exact versions for one use on the owner's MacBook: Testcontainers integration tests in a new
colima profile `demotest`. Its provenance, maintenance, advisory and licence findings stand. Its MacBook conditions
all still apply, and its rejection of the MacBook's running colima instance (Docker Engine 28.4.0) stands. What is
new: one attended use, a database for the API's `demo` profile, and its lifetime.

**Method.** On 2026-11-07 I re-read the base record and the PostgreSQL 18.6-trixie record, and read the MacBook's
installed state from the file system, without running `brew`, colima or docker:

- `sw_vers`, `uname -m`;
- the Homebrew Cellar's version directories and their `INSTALL_RECEIPT.json` files;
- the pin directory;
- the names of the colima profile directories.

I also read the current formulae from `https://formulae.brew.sh/api/formula/{colima,lima,docker}.json`.

## 1. Verdict

**APPROVE-WITH-CONDITIONS, and blocked today.**

- **The demo use adds little risk.** It is the base record's engine with one container that lives for one demo
  session and is watched by the owner.
- **The MacBook does not have the reviewed versions** (section 4). So the first condition stops everything until the
  host matches and the installed versions are reviewed.

## 2. What it is and why it is needed

The reviewed plan runs an attended demonstration on the owner's Mac of the same revision that CI tests: the Java API
in its `demo` profile, backed by a real PostgreSQL database with its migrations, and a browser the owner operates. The
base record approves the engine only for Testcontainers tests. This record approves the one container the demo needs.
It is started by a fail-closed script, `tools/demo/run-attended-demo.sh`, which another pull request writes; this
record names its path.

## 3. Provenance

Unchanged from the base record (section 3): the colima, lima and docker bottles by SHA-256, and the colima-core
v0.10.4 VM image by the SHA-512 compiled into colima 0.10.3. On 2026-11-07 homebrew-core's colima formula is still
0.10.3, with the reviewed `arm64_tahoe` bottle `a9dfd1fa0a4aee62fef75974f39f174e4da774f7ba495c43dd0bcc23633381b8`.

## 4. Maintenance and adoption: the MacBook today

| Item | Reviewed | On the MacBook (2026-11-07) | homebrew-core today |
|---|---|---|---|
| macOS | 26 (tahoe) or 15 | 26.5 (build 25F71), arm64 | — |
| colima | 0.10.3, pinned | **0.9.1**, not pinned | 0.10.3 (bottle as reviewed) |
| lima | 2.2.0, pinned | **2.0.2**, not pinned | **2.3.0** (arm64_tahoe `31d56468…092f`), unreviewed |
| docker CLI | 29.8.1, pinned | **29.1.2**, not pinned | **29.9.3** (arm64_tahoe `42aa841a…4f67`), unreviewed |
| colima profiles | `demotest` (new) | only `default` (the rejected running instance) | — |

- **How the three were installed.** All three were poured from bottles in December 2025 (receipt `time` 1765131921).
- **colima.** Version 0.9.1 would create a new profile from a colima-core 0.9.x image. The base record's Engine 28.4.0
  came from that line, and it is rejected for unfixed runc container escapes. So colima must be 0.10.3.
- **lima and the docker CLI.** Neither 2.2.0 nor 29.8.1 can be installed fresh: homebrew-core serves only its current
  versions. That leaves two ways forward:
  - a delta review of lima 2.3.0 and docker 29.9.3. The base record notes that 2.3.0 fixes the two lima advisories of
    2026-11-06;
  - or a delta review of the installed 2.0.2 and 29.1.2. lima 2.0.2 is inside GHSA-2j9v-p4xj-cjw2 (High, `<= 2.1.2`,
    fixed in 2.1.3; its summary names QEMU VMs), which the base record ruled out only for 2.2.0.

## 5. Known vulnerabilities at these versions

Unchanged from the base record for the reviewed versions: Engine 29.5.2's later advisories are mitigated, not fixed,
by digest-only pulls, no credentials, no builds, no Swarm and no plugins. The installed versions in section 4 were not
assessed here. That is the delta review condition 1 asks for.

## 6. License

Unchanged: colima MIT, lima Apache-2.0, docker CLI Apache-2.0, Docker Engine Apache-2.0.

## 7. Execution privileges

- **The demo needs only what the tests already do.**
  - colima and limactl run as the owner.
  - dockerd runs as root in the VM.
  - One container publishes one port, on host loopback only (`docker.ip` and `host_binding_ipv4` 127.0.0.1, asserted
    per container).
- **What is new.** A person, not a test fixture, starts the container. It lives for a session instead of a test run,
  and the API connects to it from the host.
- **Who can reach the port.** Loopback leaves it reachable by every process of the owner's user and by other local
  users, for the session only. The database holds synthetic data only, and the API is the one client.
- **Not a sandbox from the user.** The VM has no host mounts, so containers cannot write the owner's home directory.
  It does not isolate the session from other software running as the owner.

## 8. Direct dependencies as published

Unchanged: colima depends on lima; the VM image has its own entry; the PostgreSQL image has its own record and an
attended-demo scope extension.

## 9. Alternatives considered

- **Testcontainers through the reviewed database harness.** It could start the same container from a Gradle task on
  the test classpath, with every Testcontainers condition. That fits PR #54's PolicyCheck rule 8 better (next point).
- **A shell script calling `docker run`.** Simpler to read, but rule 8 as proposed in PR #54 refuses any docker command
  in a shell script except reads, removals and one pull by digest. The script would then need a reviewed exception in
  that rule, or it must start PostgreSQL through the harness. The pull request that writes the script settles this.
- **CI only.** No local engine at all, but no live demonstration on the owner's machine.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter. Condition 1 blocks all of them until the host matches.

**Residual risk.**

- **The owner's own account.** The demo runs next to the owner's credentials. The engine's later advisories are
  mitigated, not fixed.
- **Loopback is shared.** A port bound to 127.0.0.1 is reachable by every local process for the session.
- **A missed cleanup.** A failed cleanup could leave a container and its volume behind. The checks after the session
  are what catch that.

## 11. Not verified

- **What the MacBook will have** once it is updated: versions, bottle hashes and pins. That is condition 1's record.
- **The script**, which does not exist yet.
- **The running instance's colima and lima versions and its mount and port configuration.** Its profile directory
  `default` exists; its configuration was not read.
- **The delta review** of whichever lima and docker CLI versions will be used.
