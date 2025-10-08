#!/usr/bin/env bash
set -euo pipefail

clj=("${@:-clojure}")
export CI=1
export CLOJURE=${clj[*]}

set -x

"${clj[@]}" -T:build native
"${clj[@]}" -M:test --skip :unit
