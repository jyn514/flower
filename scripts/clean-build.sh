#!/bin/sh
# only meant for quick iteration times in dev; for real sites use graal
set -e

flower() { java -jar ~/src/flower/target/flower.jar "$@"; }

cd ~/src/flower
clojure -T:build uberjar
cd ..
rm -rf flower-test
mkdir flower-test
cd flower-test
flower new
flower configure
flower watch
