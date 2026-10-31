#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME=/run/restaurant-disk-retention
[[ ! -L "$RUNTIME" ]] || exit 2
install -d -m 700 "$RUNTIME"
exec 9<>"$RUNTIME/journal.lock"
flock -n 9 || exit 0
plan="$(mktemp "$RUNTIME/journal.XXXXXX.json")"
trap 'rm -f -- "$plan"' EXIT
python3 -I "$SCRIPT_DIR/server-journal-retention.py" >"$plan"
cat "$plan"
digest="$(sha256sum "$plan" | awk '{print $1}')"
python3 -I "$SCRIPT_DIR/server-journal-retention.py" --execute-plan "$plan" --sha256 "$digest"
