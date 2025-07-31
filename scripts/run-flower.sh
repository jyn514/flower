#!/bin/sh
here=$(dirname $0)/..
# (
# 	cd $here
# 	clojure -T:build uberjar
# )
if ! [ -e $here/deps.edn ]; then
	ln -s $here/deps.edn
fi
# java -jar $here/target/flower.jar "$@"
exec clojure -M --main flower.core "$@"
