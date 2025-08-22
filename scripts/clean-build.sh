#!/bin/sh
set -e

PATH=~/src/flower/target:$PATH
export PATH

cd ~/src/flower
(cd blossom
# rerun configure since we often modify defaults just before running this script
../target/flower configure
# run this even if configure failed
ninja flower)
# clojure -T:build native
cd ..
rm -rf flower-test
mkdir flower-test
cd flower-test
flower new
# TODO: find a way to make sure defaults are up to date, maybe in CI
flower configure
flower watch
