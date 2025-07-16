#!/bin/sh

# TODO: tell ninja which target to rebuild using WATCHEXEC_*_PATH
# https://github.com/watchexec/watchexec/tree/main/crates/cli#features
watchexec ninja &
trap "kill $!" EXIT
# npm install -g yalr

wait
