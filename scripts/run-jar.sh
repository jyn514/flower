#!/bin/sh
here=$(dirname $0)/..
java --enable-native-access=ALL-UNNAMED -jar "$here"/target/flower.jar "$@"
