---
component: "docker.io/testcontainers/ryuk"
kind: container-image
version: "0.14.0"
verdict: REJECT
scope: "None. Not approved on CI workers or on developer hosts. Testcontainers 2.0.5 runs with Ryuk disabled."
conditions: ["Every Gradle Test task sets environment TESTCONTAINERS_RYUK_DISABLED=true, so this image is never started", "CI workers do not stage or pull testcontainers/ryuk (or ghcr.io/testcontainers/ryuk); a pull attempt fails the job", "A future re-review needs: a newer release built with a supported Go toolchain, a digest pin through ryuk.container.image, ryuk.container.privileged=false proven to work, and the published port shown to be reachable only from loopback"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: `testcontainers/ryuk:0.14.0` (Testcontainers resource reaper)

## 1. Verdict

**REJECT. Ryuk is not needed, and it is over-privileged.** The design's CI workers are disposable. With
`TESTCONTAINERS_RYUK_DISABLED=true`, Testcontainers cleans up from a JVM shutdown hook instead (policy.md: ask "is it
needed?" before "how do we make it safe?").

When it does run, the risks stack up:
- **Privileges.** Testcontainers 2.0.5 starts it **privileged by default, with the Docker socket mounted**.
- **Open listener.** Ryuk has a **TCP listener with no authentication** that takes any Docker filter and
  **force-removes** the matching containers (with volumes), networks, volumes and images. Docker publishes that port on
  all host addresses by default, so on a developer's network anyone who can reach it during a test run can make Ryuk
  delete the host's Docker resources.
- **Supply chain.** The image is unsigned, has no attestation, and its tags are mutable. It was built a year ago from
  a floating `golang:1.23` tag.

## 2. What it is and why it is needed

Ryuk removes the containers, networks and volumes a test session leaves behind. Testcontainers starts it
automatically: `RyukContainer` calls `super("testcontainers/ryuk:0.14.0")`
(https://raw.githubusercontent.com/testcontainers/testcontainers-java/2.0.5/core/src/main/java/org/testcontainers/utility/RyukContainer.java).
v01-design.md §3 asks for the "PostgreSQL/Ryuk images" to be inventoried, but the design does not choose Ryuk for any
purpose of its own.

## 3. Provenance

- **Source.** `github.com/testcontainers/moby-ryuk`, tag `0.14.0`, commit `b3726afd6cc2c36628abcc08e9cabac43f587384`
  (GPG-verified), released by `github-actions` on 2025-09-26.
- **Publishing.** `publish-docker-image.yml` runs on release. It builds the Linux images with
  `docker/build-push-action@v6` (pinned by tag) and pushes them to Docker Hub and `ghcr.io/testcontainers/ryuk`;
  Windows images use `docker build` and `docker manifest`. There is **no cosign, no attestation and no `permissions:`
  block** (https://raw.githubusercontent.com/testcontainers/moby-ryuk/0.14.0/.github/workflows/publish-docker-image.yml).
- **Build.** `linux/Dockerfile` builds `FROM golang:1.23-alpine3.22` (by tag; Hub shows it last pushed 2025-08-06) with
  `CGO_ENABLED=0`, compresses with UPX, and finishes `FROM scratch` plus `ca-certificates`, with no `USER`.
- **Docker Hub.** Namespace `testcontainers`, registered 2020-06-05, with 816,331,268 pulls and immutable tags
  **disabled** (https://hub.docker.com/v2/repositories/testcontainers/ryuk).
- **Digests** (https://hub.docker.com/v2/repositories/testcontainers/ryuk/tags/0.14.0):

| Reference | Digest |
|---|---|
| manifest list, pushed 2025-09-26 | `sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0` |
| linux/amd64 | `sha256:f0456560ea5b4acdbed0da0efc33b5f9dd6bc1e59f2337106826dcb5b0b0e981` |
| linux/arm64 | `sha256:79e2fbedad76001e8ac2f64556a729de503531be9dad9e2ff6fc469006b2e93c` |

## 4. Maintenance and adoption

0.13.0 came out 2025-09-01 and 0.14.0 on 2025-09-26. There has been **nothing since, over 13 months**, and `main` is 9
commits ahead (https://github.com/testcontainers/moby-ryuk/releases). The repository has 251 stars, an MIT licence, no
`SECURITY.md` and no advisories. **Scorecard 4.8** (2026-10-26): Packaging 10, Code-Review 6, Maintained 5,
Pinned-Dependencies 1, and Token-Permissions and Security-Policy both 0.

## 5. Known vulnerabilities at 0.14.0

**Method.** OSV has no entry for the image, so the Go modules were checked with deps.dev v3 and
`https://api.osv.dev/v1/vulns/<id>` on 2026-11-06. The module itself returned 404 on deps.dev.

| Dependency | Advisories | Reachable? |
|---|---|---|
| `github.com/docker/docker` v28.3.3+incompatible | GHSA-pxq6-2prw-chj9/GO-2026-4883, GO-2026-4887, GHSA-rg2x-37c3-w2rh/GO-2026-5617, GHSA-vp62-88p7-qqf5/GO-2026-5668, GHSA-x86f-5xw2-fm2r/GO-2026-5746 | No: the symbols are in `.../daemon`; Ryuk imports only `client` and `api/types` |
| `golang.org/x/net` v0.38.0 | 18, e.g. GO-2026-4918, GO-2026-6603, GHSA-5cv4-jp36-h3mw (html, http2, idna, dnsmessage; fixed by v0.62.0) | Low: no HTTP server and no HTML; HTTP/2 client paths face the local daemon only |
| `golang.org/x/sys` v0.31.0 | GO-2026-5024 | No: Windows only |
| `github.com/moby/go-archive` v0.1.0 | GHSA-hfg8-hc9c-6c3h / GO-2026-6253 (tar traversal) | No: not imported by any non-test file |

**Go standard library.** `go.mod` says `go 1.23.9`. By OSV's ranges, any go1.23.x falls inside GO-2026-4918, -5026,
-6603, -6610, -6611, -6612 and -6617 (`net/http`, HTTP/2, idna). The exact patch version in the binary was not
verified.

## 6. License

MIT: "The MIT License (MIT) Copyright (c) 2017-2018 Sergei Egorov"
(https://raw.githubusercontent.com/testcontainers/moby-ryuk/0.14.0/LICENSE).

## 7. Execution privileges (test phase, if it were started)

- **Container.** Testcontainers sets `withPrivileged(...)` with a default of `"true"`, and binds the host's Docker
  socket to `/var/run/docker.sock`. That means full control of the Docker host, which is root-equivalent on Linux. The
  image runs as root.
- **Listener** (https://raw.githubusercontent.com/testcontainers/moby-ryuk/0.14.0/reaper.go).
  `lc.Listen(ctx, "tcp", fmt.Sprintf(":%d", r.cfg.Port))` listens on all interfaces, port 8080. There is no
  authentication, TLS or remote-address check. Each line goes to `url.ParseQuery`, every key becomes a Docker filter,
  and Ryuk replies `ACK` even on a parse error.
- **Removal.** Ten seconds after the last client disconnects (or 60 s with no connection), it removes everything that
  matches:
  - containers with `Force: true, RemoveVolumes: true`;
  - networks;
  - volumes, with force;
  - images, with `PruneChildren`.

  The only exemption is containers labelled `org.testcontainers.ryuk=true`.
- **Exposure.** Testcontainers publishes Ryuk's port with no host IP, and Docker binds it "to all host addresses
  (`0.0.0.0` and `[::]`)" by default
  (https://github.com/docker/docs/blob/main/content/manuals/engine/network/port-publishing.md).
- **Outbound.** Only the Docker API (`client.FromEnv`).

## 8. Direct dependencies as published (`go.mod`)

The direct dependencies are `caarlos0/env/v11` v11.3.1, `containerd/errdefs` v1.0.0, `docker/docker`
v28.3.3+incompatible, `moby/go-archive` v0.1.0, and `stretchr/testify` v1.10.0 (tests). There are 41 indirect modules,
among them `golang.org/x/net` v0.38.0, `x/sys` v0.31.0, `go.opentelemetry.io/otel*` v1.28.0 (no advisories) and
`sirupsen/logrus` v1.9.3.

## 9. Alternatives considered

The chosen option is `TESTCONTAINERS_RYUK_DISABLED=true`. Cleanup then happens at JVM exit, and anything a `kill -9`
leaves behind carries the label `org.testcontainers=true`. Disposable CI workers discard everything anyway.
`ghcr.io/testcontainers/ryuk` is the same build, so it is no alternative.

## 10. Conditions and residual risk

The conditions, in the frontmatter, keep the rejection effective. **Residual risk with Ryuk disabled:** a JVM that is
killed outright leaves labelled test containers, which hold test data only.

## 11. Not verified

The image was not pulled or rebuilt, so the binary was not compared with the source. Also unverified: the exact Go
patch version; whether `ryuk.container.privileged=false` works on our hosts; what the `unknown/unknown` manifests
contain; whether VM-based Docker Desktop exposes the port beyond the host. The `moby-ryuk` test files were not read.
