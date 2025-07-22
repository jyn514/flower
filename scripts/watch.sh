#!/bin/sh

# TODO: tell ninja which target to rebuild using WATCHEXEC_*_PATH
# https://github.com/watchexec/watchexec/tree/main/crates/cli#features
watchexec -w src -w lib -w scripts -w build.ninja ninja &
trap "kill $!" EXIT
python -m http.server -d public 1112 &
trap "kill $!" EXIT
# npm install -g yalr
yalr public &
trap "kill $!" EXIT
wait
