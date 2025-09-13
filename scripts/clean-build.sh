#!/bin/sh
set -e

PATH=~/src/flower/target:$PATH
export PATH

if ! [ -e build.ninja ]; then
	clojure -T:build gen-plan
fi
ninja flower-bin

cd ..
rm -rf flower-test
mkdir flower-test
cd flower-test
flower new
# TODO: find a way to make sure defaults are up to date, maybe in CI
flower build
