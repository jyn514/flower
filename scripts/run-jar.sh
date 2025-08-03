#!/bin/sh
here=$(dirname $0)/..
java -jar $here/target/flower.jar "$@"
