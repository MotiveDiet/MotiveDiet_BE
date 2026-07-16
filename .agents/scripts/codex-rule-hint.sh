#!/usr/bin/env bash
# Codex 용 룰 힌트. Claude Code 의 PreToolUse:Edit|Write 훅에 대응하지만,
# Codex 에는 동등한 훅이 없으므로 파일을 고치기 전에 직접 호출해야 한다.
#
#   .agents/scripts/codex-rule-hint.sh src/main/java/.../User.java
#
# 매칭 로직은 Claude 훅과 같은 rule-matcher.py 를 공유한다 — 두 도구가 서로 다른
# 규칙을 보는 상황을 만들지 않기 위함이다.
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "사용: $(basename "$0") <파일경로> [파일경로...]" >&2
  exit 2
fi

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for path in "$@"; do
  python3 "$DIR/rule-matcher.py" --path "$path"
done
