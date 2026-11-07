---
component: "Scope extension: docker.io/library/postgres 18.6-trixie (Docker Official Image), as the attended demo database"
kind: container-image
version: "18.6-trixie, index sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583 (linux/arm64/v8 sha256:a8a18afebc8b2b529112c931f193151b712e566b7a49563dc0f328d51f652087)"
verdict: APPROVE-WITH-CONDITIONS
scope: "Attended demo only: the database of the Java API's demo profile, created by the reviewed harness ReviewedPostgres in the colima profile of the host the owner picks (PENDING): demotest on the owner's MacBook or rigbuild on the Mac Mini, both linux/arm64. One session, attended by the owner, synthetic data only. Not for production, deployment, CI or unattended use. The engine's own record (homebrew--colima-lima-docker--attended-demo-scope.md) blocks it until the harness, its launcher and the wrapper are reviewed and the picked host's precondition holds"
conditions: ["Created only by the reviewed harness ReviewedPostgres, which references docker.io/library/postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583 alone, never by tag and never in the name:tag@sha256 form", "The image is staged by that digest before the session; the harness pulls nothing and checks it: RepoDigests contains the index digest and the platform image is linux/arm64/v8 sha256:a8a18afebc8b2b529112c931f193151b712e566b7a49563dc0f328d51f652087. No other image is in the VM", "No /docker-entrypoint-initdb.d content, no copy into the container, no bind mount and no named volume. The schema comes only from the API's Flyway migrations over JDBC, and the data only from the repository's synthetic seed migrations", "The harness generates a random password for each session and the launcher hands it to the application in-process. It is never exported to the environment, written to a file, printed, logged or committed, and it is never the default test/test or POSTGRES_HOST_AUTH_METHOD=trust", "The one published port is bound to 127.0.0.1 only, as the harness asserts, and it is never forwarded or tunnelled; on the Mac Mini only the application's HTTP port is tunnelled", "The database lives for one demo session. Closing the harness's Database removes the container with its volume, and the script's sweep afterwards checks that nothing remains; nothing is kept between sessions", "A new digest, PostgreSQL 18.7 or a CVE published against 18.6 is a new review before use; the operator checks this on the demo day", "The engine is the picked host's profile under homebrew--colima-lima-docker--attended-demo-scope.md, with all its conditions; until that record's conditions 1 and 2 and the picked variant's precondition hold, this approval cannot be used"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-07"
expiresAt: "2027-05-06"
---

# Scope extension: PostgreSQL 18.6-trixie as the attended demo database

**Not a new review.** This record extends `container-image--postgres--18.6-trixie.md` ("the base record"). The base
record approves this image by its index digest as a throwaway database for the Testcontainers tests, in CI and in the
colima profiles `rigbuild` and `demotest`. Its provenance, licence and execution findings stand.

What is new is one more use of the same digest: the database behind the API's `demo` profile, on the host the owner
picks, for one attended session. The reviewed harness `ReviewedPostgres` creates it, as in the tests. Every base
condition still applies, and this record adds lifetime, operator and data conditions:

- **Digest only.** The base conditions 1 and 2.
- **No init scripts, copies or mounts.** Base condition 3; this record adds "no named volume".
- **A random password and loopback only.** Base condition 4; the harness owns the password.
- **Removal with the volume.** Base condition 5.
- **Re-review on a new digest, 18.7 or a CVE.** Base condition 6.
- **A reviewed engine.** Base condition 7.

**Method.** On 2026-11-07:

- **Re-read** the base record and the harness `ReviewedPostgres` at pull request #54's head `9464cf3`.
- **Read** PostgreSQL's security page (`https://www.postgresql.org/support/security/`) and release roadmap
  (`https://www.postgresql.org/developer/roadmap/`).
- **Read** Docker Hub's tag document for `18.6-trixie`.
- **Fetched** both the reviewed index and the tag's current index from `registry-1.docker.io` by digest, with an
  anonymous pull token, and compared them.

Nothing was pulled into an engine or run.

## 1. Verdict

**APPROVE-WITH-CONDITIONS**, for an attended demo database that holds synthetic data, lives for one session and is
created by the reviewed harness. The image and its risks are those of the base record. A demo needs no other image, no
initialisation script and no persistent data, and the conditions keep it that way. The engine record keeps the whole
use blocked until the harness, its launcher and the wrapper are reviewed and the picked host matches the reviewed
versions. The owner's choice of host is PENDING.

## 2. What it is and why it is needed

The plan requires the API's `demo` profile to use PostgreSQL: Flyway migrations, transaction-local tenant binding,
row-level security and separate migration and runtime roles. An in-memory store would not show a database-backed demo.

The owner runs the same revision that CI tests, so the image is the one CI uses, by the same digest, created by the same
harness. At `9464cf3` that harness starts the container from the digest. It refuses an engine, an image or a container
the reviews do not allow, generates its own password, and returns a `Database` that removes the container when it is
closed.

## 3. Provenance

- **Unchanged** from the base record (section 3): index `sha256:bd5dfa49…0583`, linux/arm64/v8
  `sha256:a8a18afe…2087`, built by Docker's Official Images programme.
- **The tag has moved; the reviewed digest has not.** Since 2026-11-06T19:08:37Z the tag `18.6-trixie` points at a new
  index, `sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42`.
  - Both indexes were fetched from the registry by digest. Each body's SHA-256 equals its digest, so the reviewed index
    is still served.
  - Their 16 manifests differ only at positions 12 and 13: the linux/riscv64 image and its attestation were rebuilt.
  - The linux/amd64 (`e923a22a…a913`) and linux/arm64/v8 (`a8a18afe…2087`) images are the same in both.
  - This matters to anyone who pulls by tag. Pulling by the reviewed digest, as the conditions require, is unaffected.

## 4. Maintenance and adoption

PostgreSQL's roadmap gives the next minor releases as November 12th, 2026, after the demo. 18.7 then triggers a new
review (condition 7). Until then 18.6 is the newest 18.x.

## 5. Known vulnerabilities at this version

- **No CVE against 18.6 on 2026-11-07.** PostgreSQL's security page lists the newest CVEs (CVE-2026-19385,
  CVE-2026-18408, CVE-2026-18024 and others) with 18.6 as their fixed version, not as affected.
- **Not re-checked:** the Debian packages and gosu inside the image. Their scan is still open (base record, section 11).

## 6. License

Unchanged: the PostgreSQL License, and the packaging MIT. The image's Debian packages carry their own licences.
Nothing is redistributed. The PostgreSQL License is a permissive licence, but it is not on the list that
`security/AGENTS.md` accepts without conditions, so the pull request lists it for the owner to confirm with this use.

## 7. Execution privileges

- **Unchanged inside the engine.** The entrypoint runs as root, then `gosu postgres`. It listens on 5432 in the
  container.
- **What changes for the demo.**
  - The application connects over the loopback-published port, with the password the harness passed to it in-process.
  - The container lives for the session, not for one test run.
  - Its data is synthetic and is deleted with its volume at the end.
- **On the Mac Mini** only the application's HTTP port is tunnelled to the owner's MacBook. The database port never
  leaves the Mini's loopback.

## 8. Direct dependencies as published

Unchanged: `debian:trixie-slim`, the PGDG `postgresql-18` packages and gosu 1.19, covered as part of this digest.

## 9. Alternatives considered

- **An in-memory store.** It would not be the database-backed demo the plan requires.
- **A persistent named volume.** It would keep demo data between sessions, which the demo does not need.
- **A different tag or a newer digest.** A new review.
- **A container started by the script with `docker run`.** Rejected, as in the engine record.

## 10. Conditions and residual risk

**Conditions:** see the frontmatter.

**Residual risk.**

- **A shared loopback port.** For the session, every process of the host's user can reach the database port, though
  logging in needs the password, which stays in the JVM.
- **Unscanned packages.** The image's Debian packages and gosu were not scanned (base record).

## 11. Not verified

- **The harness's engine case for the demo profiles, the launcher and the script,** none of which exists in reviewed
  form yet.
- **That the reviewed digest stays pullable** after 2026-11-07. It was served on that day.
- **Unchanged from the base record:** the attestation contents and a CVE scan of the image's packages.
