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

require docker
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
