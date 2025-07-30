#!/bin/sh
ver=24
os=linux
arch=x64
out=graalvm-jdk.tar.gz
dir=graalvm-jdk-24.0.2+11.1

here=$(realpath "$PWD")
cd "$(dirname "$(realpath "$0")")/.." || exit 1

mkdir -p target
cd target || exit 1
if ! [ -e $out ]; then
	ls
	# exit 0
	curl -o $out https://download.oracle.com/graalvm/$ver/latest/graalvm-jdk-${ver}_${os}-${arch}_bin.tar.gz
fi
if ! [ -e $dir ]; then
	tar -xf $out
fi
JAVA_HOME=$(realpath "$dir")
PATH=$JAVA_HOME/bin:$PATH
export JAVA_HOME PATH
cd "$here" || exit
native-image "$@"
