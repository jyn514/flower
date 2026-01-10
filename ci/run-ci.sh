#!/usr/bin/env bash
set -euo pipefail

exists() {
	command -v "$1" >/dev/null 2>&1
}

fatal() {
	for arg in "$@"; do
		echo "fatal: $arg" >&2
	done
	exit 1
}

require() {
	cmd=$1
	shift
	if ! exists "$cmd"; then
		fatal "need $cmd installed to run local CI image" "$@"
	fi
}

require woodpecker-cli "install it from brew or https://github.com/woodpecker-ci/woodpecker/releases/latest"

if ! exists docker && exists podman && ! [ -n "${DOCKER_HOST:-}" ]; then
	case "$OSTYPE" in
		linux*) f=$(podman info --format '{{.Host.RemoteSocket.Path}}')
			if ! [ -e "$f" ]; then
				fatal "$f does not exist. try running 'systemctl --user start podman.socket', or install docker."
			fi
			export DOCKER_HOST=unix://$f
			;;
		darwin*) f=$(podman machine inspect --format 'unix://{{.ConnectionInfo.PodmanSocket.Path}}')
			if ! [ -e "$f" ]; then
				fatal "$f does not exist. try running 'podman machine start', or install docker."
			fi
			export DOCKER_HOST=unix://$f
			;;
		msys*|cygwin*) export DOCKER_HOST=npipe://$(podman machine inspect --format '{{.ConnectionInfo.PodmanPipe.Path}}' | tr '\' /);;
	esac
else
	require docker
fi

require clojure
require git

if [ "${1:-}" = --remote ]; then
	args="--local=false"
else
	d=target/ci-build
	mkdir -p $d
	git ls-files --exclude-standard --others --cached -z \
		| rsync -a --from0 --files-from=- ./ $d/
	git -C $d init
	git -C $d add .
	git -C $d commit -m "$(git describe --always --dirty)" --allow-empty
	args="--repo-path=$d"
fi

if exists bb; then
	bb ci/generate_ci.clj
else
	clojure -M ci/generate_ci.clj
fi

woodpecker-cli exec "$args" --backend-engine=docker --pipeline-event push --repo jyn514/flower \
	--repo-clone-url https://codeberg.org/jyn514/flower --repo-default-branch=dev \
	"--commit-branch=$(git branch --show-current)" --commit-sha="$(git rev-parse HEAD)" \
	.woodpecker/test.yaml
