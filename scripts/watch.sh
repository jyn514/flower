#!/bin/sh

# TODO: tell ninja which target to rebuild using WATCHEXEC_*_PATH
# https://github.com/watchexec/watchexec/tree/main/crates/cli#features
watchexec -w content -w build.ninja ninja &
trap "kill $!" EXIT
# npm install -g yalr
# yalr public
wait
