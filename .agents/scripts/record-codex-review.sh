#!/usr/bin/env bash
# Codex 교차 검증 결과를 커밋 가능한 기록으로 남긴다.
#
#   .agents/scripts/record-codex-review.sh <codex출력파일> [단계]
#     단계: code(기본) | plan
#
# 마커가 아니라 **Codex 의 실제 출력**을 통째로 저장한다. 이유:
#   - CI 는 커밋된 것만 본다. gitignore 된 마커는 CI 가 볼 수 없어 강제가 불가능하다
#   - 출력이 git 에 남아야 사람이 감사할 수 있다. "검증했다"는 주장만으론 근거가 없다
#
# 기록은 코드 상태 해시로 묶인다. 리뷰 이후 코드를 고치면 해시가 어긋나 게이트가 다시 막는다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="${1:-}"
STAGE="${2:-code}"

if [ -z "$SRC" ] || [ ! -f "$SRC" ]; then
  echo "사용: $(basename "$0") <codex출력파일> [code|plan]" >&2
  echo "  Codex 를 -o <파일> 로 돌려서 나온 출력을 넘겨라." >&2
  exit 2
fi

if [ ! -s "$SRC" ]; then
  echo "출력 파일이 비어 있다: $SRC" >&2
  echo "  Codex 가 실제로 뭔가 답했는지 확인할 것." >&2
  exit 1
fi

cd "$ROOT"
HASH="$(python3 .agents/scripts/check-review.py --hash)"
OUT="$ROOT/.agents/reviews/${HASH}.md"
mkdir -p "$(dirname "$OUT")"

{
  echo "---"
  echo "code_hash: $HASH"
  echo "stage: $STAGE"
  echo "at: $(date +%Y-%m-%dT%H:%M:%S%z)"
  echo "reviewer: codex"
  echo "---"
  echo
  echo "# Codex 교차 검증 — $STAGE"
  echo
  echo "이 기록은 코드 해시 \`$HASH\` 를 검증한 것이다. 이후 코드가 바뀌면 게이트가 다시 막는다."
  echo
  echo "## Codex 출력 (원문)"
  echo
  cat "$SRC"
} > "$OUT"

echo "기록: ${OUT#$ROOT/}"
echo "  코드 해시: $HASH"
echo "  이 파일을 반드시 커밋할 것 — CI 가 이걸 본다."
