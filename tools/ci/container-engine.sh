#!/usr/bin/env bash
# The runner's half of the conditions under which the database tests may start containers on a GitHub-hosted
# ubuntu-24.04 runner: security/reviews/runner-image--github-ubuntu-24.04-docker-engine--28.0.4.md (the engine),
# container-image--postgres--18.6-trixie.md (the image) and maven--testcontainers--2.0.5.md. The test harness,
# apps/shipping-api/src/test/java/io/github/stigjohnny/shipping/database/ReviewedPostgres.java, checks the other half
# from inside the test JVM before it starts PostgreSQL.
#
#   prepare  Before any container: the runner image, Ubuntu, the architecture and the Docker Engine and CLI versions,
#            runc 1.3.3 or later, and the environment the tests get; then a loopback-only daemon (a daemon.json that
#            must not exist yet, and a restart) and an empty registry configuration. The versions go into the job
#            summary, so that they stay with the run.
#   stage    Records the image set, pulls the reviewed image by its index digest, and checks its RepoDigests and
#            platform. The index digest fixes the image for each platform (the review lists linux/amd64 as
#            sha256:e923a22a...), so the digest and linux/amd64 together name the platform image that runs.
#   verify   After the tests, whatever their outcome: no image but the reviewed one appeared, no image was pulled
#            during the tests, every container created was made from the reviewed image, and nothing was copied into
#            a container; then it removes the test containers with their volumes, and their networks.
#
# It needs DOCKER_HOST and DOCKER_CONFIG in its environment, as the workflow sets them for these steps and the tests.
# Any mismatch stops the job: another version or image needs a review first, never a looser check.
set -euo pipefail

readonly IMAGE="postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583"
readonly ENGINE_VERSION="28.0.4"
readonly RUNC_FLOOR="1.3.3"
readonly SOCKET="unix:///var/run/docker.sock"
readonly STATE="${RUNNER_TEMP:?RUNNER_TEMP is not set}/container-engine"
readonly SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
# The engine keeps only its last 256 events; a window with that many cannot be shown complete.
readonly EVENT_LIMIT=256

fail() {
  echo "::error::$*"
  exit 1
}

# Whether a version (x.y.z, an optional v, anything after) is at least the floor (x.y.z).
at_least() {
  local version="${1#v}" floor="$2"
  [[ "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+) ]] || return 1
  local have=("${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}") need
  IFS=. read -r -a need <<< "$floor"
  for i in 0 1 2; do
    if (( 10#${have[i]} > 10#${need[i]} )); then return 0; fi
    if (( 10#${have[i]} < 10#${need[i]} )); then return 1; fi
  done
  return 0
}

record() {
  echo "$1"
  echo "- $1" >> "$SUMMARY"
}

images() {
  docker image ls --all --digests --no-trunc --format '{{.ID}} {{.Repository}} {{.Tag}} {{.Digest}}' | LC_ALL=C sort
}

prepare() {
  # The runner: the GitHub-hosted ubuntu-24.04 image on x64, as the review's scope says.
  [[ "${ImageOS:-}" == "ubuntu24" ]] || fail "the runner image is '${ImageOS:-unset}', not ubuntu24"
  local id version_id pretty
  id="$(. /etc/os-release && echo "$ID")"
  version_id="$(. /etc/os-release && echo "$VERSION_ID")"
  pretty="$(. /etc/os-release && echo "$PRETTY_NAME")"
  [[ "$id" == "ubuntu" && "$version_id" == "24.04" ]] || fail "the runner runs $pretty, not Ubuntu 24.04"
  [[ "$(uname -m)" == "x86_64" ]] || fail "the runner is $(uname -m), not x86_64"

  # The environment of these steps and of the tests: the local socket, an isolated registry configuration, no
  # credentials, TLS or context, no Testcontainers setting but the two the reviews require, no user properties file.
  [[ "${DOCKER_HOST:-}" == "$SOCKET" ]] || fail "DOCKER_HOST is '${DOCKER_HOST:-unset}', not $SOCKET"
  [[ -n "${DOCKER_CONFIG:-}" ]] || fail "DOCKER_CONFIG is not set"
  local name
  for name in DOCKER_AUTH_CONFIG DOCKER_CERT_PATH DOCKER_TLS_VERIFY DOCKER_CONTEXT; do
    [[ -z "${!name+set}" ]] || fail "$name is set; the tests use no registry credentials, TLS or context"
  done
  while IFS= read -r name; do
    [[ "$name" == "TESTCONTAINERS_RYUK_DISABLED" || "$name" == "TESTCONTAINERS_CHECKS_DISABLE" ]] \
      || fail "$name is set; only TESTCONTAINERS_RYUK_DISABLED and TESTCONTAINERS_CHECKS_DISABLE may be"
  done < <(compgen -e | grep '^TESTCONTAINERS_' || true)
  [[ ! -e "$HOME/.testcontainers.properties" ]] || fail "~/.testcontainers.properties exists"

  # An empty registry configuration: no credentials and no credential helper, for every docker command
  # here and for the test JVM; created before the first of them.
  mkdir "$DOCKER_CONFIG"
  printf '{}\n' > "$DOCKER_CONFIG/config.json"

  # A loopback-only daemon: every published port binds 127.0.0.1 unless a container asks otherwise, which the tests
  # assert it does not. The runner image writes no daemon.json; if one exists, the image changed and needs a review.
  if sudo test -e /etc/docker/daemon.json; then
    echo "The runner's /etc/docker/daemon.json, for a delta review:"
    sudo cat /etc/docker/daemon.json
    fail "/etc/docker/daemon.json already exists; the runner image changed, so the engine needs its review again"
  fi
  printf '{"ip":"127.0.0.1"}\n' | sudo tee /etc/docker/daemon.json > /dev/null
  sudo systemctl restart docker
  local waited=0
  until docker info > /dev/null 2>&1; do
    (( waited < 60 )) || fail "the engine did not come back within 60 seconds of its restart"
    sleep 1
    waited=$((waited + 1))
  done
  [[ "$(sudo cat /etc/docker/daemon.json)" == '{"ip":"127.0.0.1"}' ]] || fail "daemon.json is not the one written"

  # The engine: exactly the reviewed Docker Engine and CLI, a runc the review accepts, on Ubuntu 24.04 x64.
  local client server api components runc containerd info
  client="$(docker version --format '{{.Client.Version}}')"
  server="$(docker version --format '{{.Server.Version}}')"
  api="$(docker version --format '{{.Server.APIVersion}}')"
  components="$(docker version --format '{{range .Server.Components}}{{.Name}} {{.Version}} {{index .Details "GitCommit"}}{{"\n"}}{{end}}')"
  info="$(docker info --format '{{.ServerVersion}}|{{.OperatingSystem}}|{{.KernelVersion}}|{{.OSType}}|{{.Architecture}}')"
  runc="$(awk '$1 == "runc" { print $2 }' <<< "$components")"
  containerd="$(awk '$1 == "containerd" { print $2 }' <<< "$components")"

  echo "## Container engine" >> "$SUMMARY"
  record "Runner image: ${ImageOS} ${ImageVersion:-unknown} ($pretty, $(uname -m))"
  record "Docker Engine: ServerVersion $server, ApiVersion $api, client $client"
  record "Engine report: $info"
  while IFS= read -r line; do
    [[ -z "$line" ]] || record "Component: $line"
  done <<< "$components"

  [[ "$server" == "$ENGINE_VERSION" ]] \
    || fail "Docker Engine is $server, not the reviewed $ENGINE_VERSION: another version needs its own review first"
  [[ "$client" == "$ENGINE_VERSION" ]] || fail "the CLI is $client, not the reviewed $ENGINE_VERSION"
  [[ "${info%%|*}" == "$ENGINE_VERSION" ]] || fail "the engine reports ServerVersion ${info%%|*}"
  [[ "$info" == *"|Ubuntu 24.04"* && "$info" == *"|linux|x86_64" ]] || fail "the engine reports $info"
  [[ -n "$containerd" ]] || fail "the engine reports no containerd version"
  at_least "$runc" "$RUNC_FLOOR" \
    || fail "runc is '${runc:-unset}'; the review accepts $RUNC_FLOOR or later and rejects 1.2.x"
}

stage() {
  mkdir -p "$STATE"
  images > "$STATE/images-before"
  docker pull --quiet postgres@sha256:bd5dfa494a5a259704365813dbfa5fa6b3e69be7348d43b547f540c730478583
  local digests platform image_id
  digests="$(docker image inspect --format '{{json .RepoDigests}}' "$IMAGE")"
  platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$IMAGE")"
  image_id="$(docker image inspect --format '{{.Id}}' "$IMAGE")"
  [[ "$digests" == *"\"$IMAGE\""* ]] || fail "the staged image's RepoDigests $digests do not contain $IMAGE"
  [[ "$platform" == "linux/amd64" ]] || fail "the staged image is $platform, not linux/amd64"
  echo "$image_id" > "$STATE/image-id"
  record "Staged image: $IMAGE ($platform, image ID $image_id)"
  # The test JVM gets DOCKER_CONFIG as prepare made it. What the CLI added there for itself (its plugins' state) is
  # removed; a changed config.json is a finding.
  [[ "$(cat "$DOCKER_CONFIG/config.json")" == "{}" ]] || fail "the registry configuration changed during the staging"
  local extra
  extra="$(find "$DOCKER_CONFIG" -mindepth 1 -maxdepth 1 ! -name config.json -printf '%f ')"
  if [[ -n "$extra" ]]; then
    echo "The CLI created these in DOCKER_CONFIG; removed before the tests: $extra"
    find "$DOCKER_CONFIG" -mindepth 1 -maxdepth 1 ! -name config.json -exec rm -rf {} +
  fi
  # The test window starts after the pull, so that its events are not the tests' own.
  sleep 1
  date +%s > "$STATE/since"
}

verify() {
  [[ -s "$STATE/since" && -s "$STATE/images-before" ]] || fail "nothing was staged, so there is nothing to verify"
  local since until problems=0
  since="$(cat "$STATE/since")"
  until="$(date +%s)"

  # The image set: the reviewed image is the only one the job added.
  local added
  added="$(LC_ALL=C comm -13 "$STATE/images-before" <(images))"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    if [[ "$line" != "$(cat "$STATE/image-id") postgres <none> sha256:${IMAGE##*:}" ]]; then
      echo "::error::An image that has no review appeared: $line"
      problems=1
    fi
  done <<< "$added"

  # The test window's events: what was pulled, what containers were made from, and whether anything was copied in.
  local events count
  events="$(docker events --since "$since" --until "$until" \
    --format '{{.Type}} {{.Action}} {{.Actor.ID}} {{index .Actor.Attributes "image"}}')"
  count="$(grep -c . <<< "$events" || true)"
  if (( count >= EVENT_LIMIT )); then
    echo "::error::The tests produced $count engine events; the engine keeps $EVENT_LIMIT, so the check cannot be complete"
    problems=1
  fi
  local creates=0 type action actor image
  while read -r type action actor image; do
    [[ -z "$type" ]] && continue
    if [[ "$type" == "container" && "$action" == "create" ]]; then
      creates=$((creates + 1))
      if [[ "$image" != "$IMAGE" ]]; then
        echo "::error::A container was made from $image, not the reviewed image"
        problems=1
      fi
    elif [[ "$type" == "container" && "$action" == "extract-to-dir" ]]; then
      echo "::error::Files were copied into a container ($actor); the runner review forbids the archive API"
      problems=1
    elif [[ "$type" == "image" && "$action" =~ ^(pull|import|load|tag|push)$ ]]; then
      echo "::error::The tests used the engine to $action an image ($actor); they pull nothing"
      problems=1
    fi
  done <<< "$events"
  record "Test window: $count engine events, $creates containers made from the reviewed image"

  # Cleanup: the label sweep removes the test containers with their anonymous volumes, then their networks.
  local id
  for id in $(docker ps -aq --filter label=org.testcontainers=true); do
    docker rm -f -v "$id" > /dev/null
  done
  for id in $(docker network ls -q --filter label=org.testcontainers=true); do
    docker network rm "$id" > /dev/null
  done
  [[ -z "$(docker ps -aq --filter label=org.testcontainers=true)" ]] || fail "test containers are still there"
  (( problems == 0 )) || fail "the tests used the engine beyond the reviewed image; see the errors above"
}

case "${1:-}" in
  prepare | stage | verify) "$1" ;;
  *) echo "usage: container-engine.sh prepare|stage|verify" >&2; exit 2 ;;
esac
