---
component: "docker.io/library/postgres (Docker Official Image)"
kind: container-image
version: "18.6-trixie"
verdict: APPROVE-WITH-CONDITIONS
scope: "Test phase only: the database container for this repository's Testcontainers 2.0.5 integration tests of :example-app, on linux/amd64 (GitHub-hosted ubuntu-24.04 CI) and linux/arm64 (the colima rigbuild and demotest VMs), pulled by the index digest below. Not for production, deployment, workflow `services:`/`container:`, or any other platform. The 18.6-bookworm and alpine variants are not approved."
conditions: ["Reference only docker.io/library/postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583; in Testcontainers `DockerImageName.parse(\"postgres@sha256:<that digest>\")`, never the `name:tag@sha256:` form", "Before each run, `docker image inspect` RepoDigests must contain that index digest; the platform image must be one of the two per-platform digests in section 3", "No /docker-entrypoint-initdb.d content, no copy-to-container, no bind mounts; schema only through Flyway over JDBC", "A random per-run password (withPassword), never the default test/test; published port bound to 127.0.0.1 (asserted by the test)", "Containers removed with `docker rm -f -v` so the anonymous volume for /var/lib/postgresql goes too", "A new digest (the 18.6 tag is rebuilt on base-image updates), PostgreSQL 18.7, or a CVE published against 18.6 is a new review before use; expiresAt is an upper bound", "The engine that runs it needs its own review: colima-review.md (local) and runner-image--github-ubuntu-24.04-docker-engine--28.0.4.md (CI)"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# `postgres:18.6-trixie` (Docker Official Image) for the integration tests

The image record that both colima hosts and CI pull by digest. Nothing was pulled or run; digests and Dockerfiles were read from Docker Hub's API and the docker-library repositories.

## 1. Verdict
**APPROVE-WITH-CONDITIONS**, Debian **trixie** variant, by index digest, as a throwaway test database. PostgreSQL 18.6 is the newest 18.x release and fixes 28 CVEs; the image is built and published by Docker's Official Images programme from a public, GPG-checked Dockerfile; amd64 and arm64 install PGDG binary packages. Trixie over bookworm: it is the default suite (`18.6` and `18.6-trixie` share one digest), and Debian 12 bookworm left regular security support on 2026-07-12 for the LTS team (https://www.debian.org/News/2026/20260712). The conditions keep it a disposable, local-only test fixture, because the image's Debian packages were not scanned (section 11).

## 2. What it is and why it is needed
`v01-design.md` §4: "PostgreSQL 18.6 … Testcontainers 2.0.5 … Example only: real transactions, migrations and isolated databases … Reject H2 substitution". §6 slices 5 and 10 test joint commit/rollback and a deliberately broken audit transaction. §3: "images use digests".

## 3. Provenance
- Publisher: Docker Official Images. "Docker is responsible for building and publishing the images on Docker Hub" (https://docs.docker.com/docker-hub/repos/manage/trusted-content/official-images/). Hub reports `last_updater_username` `doijanky`, media type `application/vnd.oci.image.index.v1+json`.
- Library entry (https://raw.githubusercontent.com/docker-library/official-images/master/library/postgres): maintainers Tianon Gravi (@tianon) and Joseph Ferguson (@yosifkit); GitRepo `https://github.com/docker-library/postgres.git`; Tags `18.6, 18, latest, 18.6-trixie, 18-trixie, trixie`; GitCommit `e00e1bd34ec5c8a8e7ad89b273b3d42efaf6d5bc`; Directory `18/trixie`.
- Digests (https://hub.docker.com/v2/repositories/library/postgres/tags/18.6-trixie, pushed 2026-11-05T10:14:13Z; cross-checked with https://raw.githubusercontent.com/docker-library/repo-info/master/repos/postgres/remote/18.6-trixie.md):
  - index `sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583` (also tag `18.6`)
  - linux/amd64 `sha256:e923a22ac074261dd73970c57edf659475bf0497cb9456f17d98ed7cdd3ca913` (162,499,641 bytes)
  - linux/arm64/v8 `sha256:a8a18afebc8b2b529112c931f193151b712e566b7a49563dc0f328d51f652087` (161,112,720 bytes)
  - For comparison, `18.6-bookworm` index is `sha256:f1ea2a1a4fb7b68d4836be1fdcb8a6466a89b072158bf9d97663600538c294c5` (not approved).
- Build (https://raw.githubusercontent.com/docker-library/postgres/e00e1bd34ec5c8a8e7ad89b273b3d42efaf6d5bc/18/trixie/Dockerfile): `FROM debian:trixie-slim`; `PG_VERSION 18.6-1.pgdg13+2` from `apt.postgresql.org … trixie-pgdg main 18`, verified by apt against key `B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8` (fetched over plain http; integrity rests on the apt signature). amd64 and arm64 use PGDG binaries; other architectures compile from source. gosu 1.19 from `github.com/tianon/gosu` releases, checked with `gpg --verify` against `B42F6819007F00F88E364FD4036A9C25BF357DD4`.
- Attestations: the index carries one `unknown/unknown` manifest per platform with `application/vnd.in-toto+json` layers (amd64 `sha256:9676119228eeb6a3405ee1aa59e7b7a0d2d0c083e90eda6c09eb963856beab40`; arm64 most likely `sha256:56c09e118dd8781c8c2dd69dad49984996a1db51294eb2ad0c69fdef5b50669e`, paired by push time). Their predicates (SLSA provenance, SBOM) were not read. Docker is "retiring Docker Content Trust (DCT) for Docker Official Images" (official-images page above), so no signature was verified.
- `sha256` in the allowlist is the index digest as published by Docker Hub's API (above).

## 4. Maintenance and adoption
- PostgreSQL: tag `REL_18_6` "Stamp 18.6." 2026-08-11; release 2026-08-13; "18.5 was never released, due to a regression discovered post-wrap"; earlier `REL_18_4` 2026-05-11 (https://github.com/postgres/postgres/tags, https://raw.githubusercontent.com/postgres/postgres/REL_18_6/doc/src/sgml/release-18.sgml). Minor releases come at least quarterly (https://www.postgresql.org/support/versioning/); the next is likely due in November 2026 (not verified), which triggers re-review.
- Image: rebuilt whenever the base changes; this digest's base layers date from 2026-11-04/05 (repo-info). The tag will move to new digests; the approval does not.
- docker-library/postgres and gosu: no published security advisories (https://github.com/docker-library/postgres/security/advisories, https://github.com/tianon/gosu/security/advisories). gosu 1.20 (2026-10-25) is newer than the 1.19 in this image.
- OpenSSF Scorecard: not checked.

## 5. Known vulnerabilities at this version
- PostgreSQL 18.6: no CVE found that affects 18.6 on 2026-11-06; 18.6 is the latest 18.x. It fixes 28 CVEs, among them CVE-2026-6471, -14663, -6464, -16239, -14669, -14664, -18024, -14680, -6470, -14666, -14681 (release-18.sgml above). OSV `POST /v1/query` was not available to this job.
- Debian trixie packages in the image: **not scanned.** No scanner was run and no Docker Scout data was read.
- gosu 1.19: built with Go 1.24.6; Go stdlib CVEs at that version were not checked. gosu runs once, at container start, to drop root.

## 6. License
PostgreSQL: the PostgreSQL License, a permissive BSD/MIT-style licence ("Portions Copyright (c) 1996-2026, PostgreSQL Global Development Group", https://raw.githubusercontent.com/postgres/postgres/REL_18_6/COPYRIGHT). Packaging repo: MIT (https://github.com/docker-library/postgres). The image "likely also contain[s] other software which may be under other licenses" (https://raw.githubusercontent.com/docker-library/docs/master/postgres/README.md). Fine for test-only use; nothing is redistributed.

## 7. Execution privileges (test phase, inside the reviewed engine)
- Starts as root (no `USER`); `docker-entrypoint.sh` chowns `PGDATA` and then `exec gosu postgres` (uid 999). `ENTRYPOINT ["docker-entrypoint.sh"]`, `CMD ["postgres"]`, `EXPOSE 5432`, `STOPSIGNAL SIGINT`, `VOLUME /var/lib/postgresql`, `PGDATA /var/lib/postgresql/18/docker` (same Dockerfile).
- The entrypoint runs `initdb` and any file in `/docker-entrypoint-initdb.d`; condition 3 keeps that empty.
- Testcontainers 2.0.5 `PostgreSQLContainer`: not privileged, no binds or file copies, `postgres -c fsync=off`, waits for "database system is ready to accept connections" twice, 60 s timeout; accepts only images compatible with `postgres` or `pgvector/pgvector` (https://raw.githubusercontent.com/testcontainers/testcontainers-java/2.0.5/modules/postgresql/src/main/java/org/testcontainers/postgresql/PostgreSQLContainer.java). `DockerImageName.parse` treats `@sha256:` as a digest, but in the `name:tag@sha256:` form the tag stays inside the repository name, hence condition 1 (`DockerImageName.java` at 2.0.5).
- Network: listens on 5432; has the default bridge's outbound access, which the server does not use unprompted. No native code beyond PostgreSQL itself; no build-time execution in this repository.

## 8. Direct dependencies as published
- `debian:trixie-slim` (Docker Official Image): the base OS. Covered as part of this digest only.
- `postgresql-18=18.6-1.pgdg13+2` and its apt dependencies (not enumerated): from the PGDG apt repository.
- gosu 1.19: privilege drop, as above.
- No separate review needed while the image is pinned by digest; flag gosu's Go version for the next review.

## 9. Alternatives considered
- `18.6-bookworm`: same PGDG binaries, but on Debian 12 in LTS. `18.6-alpine3.24`: musl, different build path; not evaluated.
- H2 or embedded PostgreSQL: rejected by `v01-design.md` §4.

## 10. Conditions and residual risk
Frontmatter conditions 1-7 apply. On the colima hosts the image is pulled by digest and retagged locally (colima review §10.2); in CI it is referenced by digest directly.

**Residual risk.** Unscanned Debian packages and gosu's Go runtime may carry known CVEs; they matter only if an attacker can reach the server, which conditions 3 and 4 limit to the test process on the same host. The image runs root during entrypoint setup inside engines that have unfixed advisories (the engine reviews). An old digest may stop being pullable once Docker Hub drops it; then a new review is needed.

## 11. Not verified
- Contents of the attestation manifests; any signature (none checked).
- A CVE scan of the Debian packages and gosu; OSV `POST /v1/query`; Scorecard.
- That Docker Hub keeps old Official-Image digests pullable.
- The arm64 attestation pairing (inferred from push time); the full apt dependency list.
- The date of the next PostgreSQL minor release.
