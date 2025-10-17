#!/usr/bin/env bash
set -euo pipefail

if [ "${1:-}" = --remote ]; then
	args="--local=false"
else
	d=target/ci-build
	mkdir -p $d
	git ls-files --exclude-standard --others --cached -z \
		| rsync -av --from0 --files-from=- ./ $d/
	git -C $d init
	git -C $d commit --allow-empty -m empty
	args="--repo-path=$d"
fi

ci/generate_ci.clj
woodpecker-cli exec "$args" --pipeline-event push --repo jyn514/flower \
	--repo-clone-url https://codeberg.org/jyn514/flower --repo-default-branch=dev \
	"--commit-branch=$(git branch --show-current)" --commit-sha="$(git rev-parse HEAD)" \
	.woodpecker/test.yaml
