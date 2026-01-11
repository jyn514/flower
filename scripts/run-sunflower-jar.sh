#!/bin/sh
here=$(dirname $0)/..
java --enable-native-access=ALL-UNNAMED -cp "$here"/target/flower.jar clojure.main -m flower.bin.sunflower
