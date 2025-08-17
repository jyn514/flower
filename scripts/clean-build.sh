#!/bin/sh
set -e

PATH=~/src/flower/target:$PATH
export PATH

cd ~/src/flower
# only meant for quick iteration times in dev; for real sites use graal
(cd blossom && ninja ../target/flower.jar)
# clojure -T:build native
cd ..
rm -rf flower-test
mkdir flower-test
cd flower-test
flower new
flower watch
