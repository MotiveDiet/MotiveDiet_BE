#!/usr/bin/env bash
# Codex 교차 검증을 마쳤음을 기록한다. quality-gate.py 가 이 기록을 확인한다.
#
#   .agents/scripts/mark-codex-review.sh <발견건수> [메모]
#
# 현재 diff 의 해시를 함께 저장한다 — 검증 이후 코드가 바뀌면 게이트가 다시 막는다.
# "리뷰 받고 나서 조용히 고치기"를 방지하기 위함이다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MARKER="$ROOT/.agents/tasks/codex-review.json"

if [ $# -lt 1 ]; then
  echo "사용: $(basename "$0") <발견건수> [메모]" >&2
  echo "  예: $(basename \"\$0\") 4 'state 누락, email 식별 등'" >&2
  exit 2
fi

FINDINGS="$1"
NOTE="${2:-}"

cd "$ROOT"
HASH="$( { git diff origin/main; git diff HEAD; } | shasum -a 256 | cut -c1-16 )"

mkdir -p "$(dirname "$MARKER")"
python3 - "$MARKER" "$HASH" "$FINDINGS" "$NOTE" <<'PY'
import json, sys, datetime
path, h, findings, note = sys.argv[1:5]
json.dump({
    "diff_hash": h,
    "findings": findings,
    "note": note,
    "at": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
}, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY

echo "기록: Codex 교차 검증 (발견 ${FINDINGS}건, diff ${HASH})"
