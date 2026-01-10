#!/bin/sh
set -eu

cache=$1

if ! [ -e "$cache/bin/clojure" ]; then
	if ! command -v curl >/dev/null 2>&1; then
		apk add --no-interactive curl
	fi
	curl -L -o "${cache}/linux-install" https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
	bash "${cache}/linux-install" --prefix "$cache"
fi

