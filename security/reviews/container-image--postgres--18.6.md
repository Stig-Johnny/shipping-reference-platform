---
component: "docker.io/library/postgres"
kind: container-image
version: "18.6"
verdict: APPROVE-WITH-CONDITIONS
scope: "Integration tests of :example-app only, started by Testcontainers on disposable CI workers and local build hosts; referenced only by image-index digest sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583. Never production, never real data."
conditions: ["Referenced only by digest: postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583 (never a tag), through org.testcontainers.postgresql.PostgreSQLContainer", "Ephemeral test databases only, holding no real or personal data; credentials are test defaults", "No /docker-entrypoint-initdb.d scripts, extra bind mounts, or POSTGRES_HOST_AUTH_METHOD=trust", "Published port not reachable from other machines on developer hosts (see the Testcontainers review)", "CI workers stage the image by digest; pulls of other images are blocked", "Any other digest, including a rebuild of 18.6, is a new (short) review; PostgreSQL 18.7 or later is a new review"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Docker Official Image `postgres:18.6` (example application tests only)

## 1. Verdict

**APPROVE-WITH-CONDITIONS, by digest only.** PostgreSQL 18.6 is the current 18.x minor release. There is no 18.7, and
18.5 was never released. No PostgreSQL CVE listed against version 18 is open at 18.6. The image is a Docker Official
Image, built from `docker-library/postgres` commit `e00e1bd3…` on `debian:trixie-slim`, with PostgreSQL from the signed
PGDG apt repository. The `18.6` tag is **mutable**: it was re-pushed on 2026-11-05 and moves with every base-image
rebuild, so the approval covers one image-index digest. The OS packages inside were not scanned. That is acceptable for
a throw-away test database holding no real data, and it is recorded as residual risk.

## 2. What it is and why it is needed

v01-design.md §4: "PostgreSQL 18.6 … Example only: real transactions, migrations and isolated databases. … Reject H2
substitution." Testcontainers starts it for the example's integration tests (slices 5 and 10).

## 3. Provenance

- **Publisher.** The Docker Official Images program. `library/postgres` lists maintainers Tianon Gravi and Joseph
  Ferguson. The entry `Tags: 18.6, 18, latest, 18.6-trixie, …` has `GitCommit: e00e1bd34ec5c8a8e7ad89b273b3d42efaf6d5bc`
  and `Directory: 18/trixie`, across 8 architectures
  (https://github.com/docker-library/official-images/blob/master/library/postgres).
- **Recipe**
  (https://github.com/docker-library/postgres/blob/e00e1bd34ec5c8a8e7ad89b273b3d42efaf6d5bc/18/trixie/Dockerfile):
  - **Base and gosu.** `FROM debian:trixie-slim` (by tag). gosu 1.19 is verified with `gpg --verify` against key
    `B42F6819007F00F88E364FD4036A9C25BF357DD4`.
  - **PostgreSQL.** The PGDG key `B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8` comes from `keyserver.ubuntu.com`, and the
    repository is `http://apt.postgresql.org` with `signed-by`. `PG_VERSION 18.6-1.pgdg13+2`. Binary packages are used
    on amd64, arm64, loong64 and ppc64el; the other architectures build from source packages.
- **Digests** (https://hub.docker.com/v2/repositories/library/postgres/tags/18.6; `18.6-trixie` is the same index). The
  publisher's `repo-info` lists the same index
  (https://github.com/docker-library/repo-info/blob/master/repos/postgres/remote/18.6.md).

| Reference | Digest |
|---|---|
| image index (OCI), pushed 2026-11-05T10:14:03Z | `sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583` |
| linux/amd64 | `sha256:e923a22ac074261dd73970c57edf659475bf0497cb9456f17d98ed7cdd3ca913` |
| linux/arm64/v8 | `sha256:a8a18afebc8b2b529112c931f193151b712e566b7a49563dc0f328d51f652087` |

- **Attestations.** Each platform is paired with an "unknown; unknown" manifest whose layers are
  `application/vnd.in-toto+json`, the media type used for attestations. They were not opened or verified.

## 4. Maintenance and adoption

PostgreSQL 18 was first released 2025-09-25 and is supported until 2030-11-14
(https://www.postgresql.org/support/versioning/). 18.6 was released 2026-08-13
(https://www.postgresql.org/docs/release/18.6/). The image is rebuilt often. The 18.x tags run from 18.0 to 18.6, 38 in
all with none newer, and 18.6 was last pushed 2026-11-05
(https://hub.docker.com/v2/repositories/library/postgres/tags?page_size=100&name=18.). Scorecard for
`docker-library/postgres` was not queried.

## 5. Known vulnerabilities at 18.6

PostgreSQL's security page lists CVEs affecting 18 as fixed in 18.1 (2), 18.2 (5), 18.4 (11) and 18.6 (28; for example
CVE-2026-19385 and CVE-2026-16238) (https://www.postgresql.org/support/security/). **No CVE listed against 18 is open at
18.6; none found on 2026-11-06.** OSV has no entry for the image itself. The Debian packages and gosu inside were **not
scanned**, because no approved scanner was available here.

## 6. License

PostgreSQL is under the PostgreSQL License, "a liberal Open Source license, similar to the BSD or MIT licenses"
(https://www.postgresql.org/about/licence/). The Dockerfiles and scripts carry an MIT-style text, "Copyright (c) 2014,
Docker PostgreSQL Authors" (https://github.com/docker-library/postgres/blob/master/LICENSE). The Debian packages have
their own licences, which were not inventoried.

## 7. Execution privileges (test phase, inside a container)

- **User.** There is no `USER` instruction, so the container starts as root. The entrypoint steps down to `postgres`
  (uid 999) with `gosu`.
- **Initialisation** (`docker-entrypoint.sh` at the same commit).
  - On an empty `PGDATA` it runs `initdb`.
  - It runs `/docker-entrypoint-initdb.d/*`: `.sh` files are executed or sourced, and `.sql` files go to `psql`.
    Testcontainers mounts none.
  - It exits if `POSTGRES_PASSWORD` is empty, unless `POSTGRES_HOST_AUTH_METHOD=trust` is set, which appends
    `host all all all trust`.
  - Testcontainers sets `POSTGRES_PASSWORD=test` and runs `postgres -c fsync=off`.
- **Network.** The sample configuration sets `listen_addresses = '*'`, with `EXPOSE 5432`. The Hub page says the image
  "sets up `trust` authentication locally" (https://hub.docker.com/_/postgres). Testcontainers publishes the port with
  no host IP, and Docker binds that to all host addresses by default.
- **Host access.** None beyond the container: no socket mount, and not privileged.

## 8. Direct dependencies as published (image contents)

The base is `debian:trixie-slim`. From PGDG it installs `postgresql-common`, `postgresql-18=18.6-1.pgdg13+2` and
`postgresql-18-jit`. It also carries gosu 1.19, GPG-verified at build time. The full package list was not inventoried;
`repo-info` lists the layers.

## 9. Alternatives considered

`18.6-alpine` (index `sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395`) is smaller and uses
musl, but its PostgreSQL is not a PGDG package. `18.6-bookworm` is the older Debian release. H2 is rejected by the
design.

## 10. Conditions and residual risk

The conditions are those in the frontmatter. **Residual risk:** the vulnerability status of the OS packages is
unknown. The upstream tag is mutable; old digests are expected to stay pullable, but that was not verified. The PGDG
key's fingerprint comment says "[expires: 2019-07-02]", and its current validity was not checked. On developer hosts,
the published port exposes known test credentials during runs unless the Testcontainers port condition holds.

## 11. Not verified

The image was not pulled, so its layers and packages were not inspected. The attestation manifests and signatures were
not verified. The image was not compared with a rebuild from `e00e1bd3`. No CVE checks were run on the Debian packages
or gosu. Platforms other than amd64 and arm64 were not examined.
