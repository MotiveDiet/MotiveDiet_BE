#!/usr/bin/env bash
# Codex 교차 검증(2중 검증 루프 3단계)을 실행하고 결과를 기록한다.
#
#   .agents/scripts/codex-review.sh "<리뷰 프롬프트>"
#
# ## 왜 스크립트인가 — 문서로 두면 안 되는 이유
#
# 이 플래그들을 README 에 "이렇게 치라"고 적어두면 아무도 강제하지 못한다.
# -s read-only 로 돌려서 Codex 가 자기 수정을 검증하지 못해도, 게이트는 리뷰 기록의
# 존재만 보므로 그대로 통과한다. 플래그가 산문이면 지켜지는지 확인할 방법이 없다.
# 스크립트가 실행하면 플래그는 코드가 되고, 실수로 잘못 칠 여지가 사라진다.
#
# ## 플래그 근거 (2026-07-16 실측)
#
#   -s workspace-write        Codex 가 결함을 직접 수정하게 한다 (read-only 면 수정 불가)
#   --add-dir ~/.gradle       Gradle 캐시·락이 워크스페이스 밖이라 쓰기 허용이 필요하다.
#                             없으면 Codex 가 빌드를 못 돌려 자기 수정을 검증하지 못한다
#   network_access=true       Gradle 의 FileLockContentionHandler 가 로컬 소켓을 연다.
#                             인터넷이 필요한 게 아니라 로컬 소켓 때문이다
#   -o <파일>                 출력을 파일로 받는다. 그대로 두면 Codex 출력 전문이
#                             Claude 컨텍스트로 들어와 그만큼 과금된다
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROMPT="${1:-}"

if [ -z "$PROMPT" ]; then
  cat >&2 <<'USAGE'
사용: codex-review.sh "<리뷰 프롬프트>"

프롬프트 작성 원칙:
  - 결론을 주지 마라. "X가 버그 같은데 맞나?" 는 동조만 부르고 교차 검증이 무의미해진다
  - 수정을 시킬 때는 제약을 알려줘라 (AGENTS.md 지뢰). 모르면 "고쳤다"며 프로덕션을 깨뜨린다
  - 구체적 재현 시나리오를 요구하라. 일반론은 쓸모없다
USAGE
  exit 2
fi

command -v codex >/dev/null || { echo "codex CLI 가 없다. PATH 를 확인할 것." >&2; exit 1; }

cd "$ROOT"

# 변경 파일에 걸린 프로젝트 룰을 프롬프트 앞에 자동으로 붙인다.
# Claude 는 PreToolUse 훅으로 룰을 보지만 Codex 는 훅이 없다 — 사람이 손으로 넣는 걸
# 잊으면 지뢰(AGENTS.md)를 모른 채 수정해 프로덕션을 깨뜨린다. 룰은 상시 제약이지
# 특정 코드에 대한 결론이 아니므로 동조를 유발하지 않는다. 매칭은 codex-rule-hint.sh
# (= Claude 훅과 같은 rule-matcher.py) 를 공유한다.
CHANGED="$(
  { git diff --name-only HEAD 2>/dev/null || true
    git diff --name-only --cached 2>/dev/null || true
    git diff --name-only origin/main...HEAD 2>/dev/null || true
    git status --porcelain 2>/dev/null | sed -n 's/^?? //p'   # untracked (새 파일)도 포함
  } | sort -u
)"
FILES=()
while IFS= read -r f; do
  [ -n "$f" ] && [ -f "$f" ] && FILES+=("$f")
done <<EOF
$CHANGED
EOF

CONTEXT=""
if [ "${#FILES[@]}" -gt 0 ]; then
  RULES="$("$ROOT/.agents/scripts/codex-rule-hint.sh" "${FILES[@]}" 2>/dev/null || true)"
  [ -n "$RULES" ] && CONTEXT="반드시 지킬 프로젝트 룰:
$RULES

"
  CONTEXT="${CONTEXT}리뷰 대상 변경 파일:
$(printf '  %s\n' "${FILES[@]}")

"
fi
PROMPT="${CONTEXT}${PROMPT}"

OUT="$(mktemp -t codex-review)"
trap 'rm -f "$OUT"' EXIT

echo "Codex 교차 검증 실행 중…" >&2
rc=0
codex exec \
  -s workspace-write \
  --add-dir "$HOME/.gradle" \
  -c sandbox_workspace_write.network_access=true \
  -C "$ROOT" \
  --skip-git-repo-check \
  -o "$OUT" \
  "$PROMPT" >/dev/null 2>&1 || rc=$?

# 종료 코드를 삼키면 안 된다. codex 가 죽었는데 출력 파일에 뭐라도 있으면
# 그걸 리뷰 기록으로 남겨 게이트를 통과시키게 된다 (2026-07-16 Codex 리뷰 지적).
if [ "$rc" -ne 0 ]; then
  echo "codex 가 실패했다 (exit=$rc). 리뷰로 기록하지 않는다." >&2
  [ -s "$OUT" ] && { echo "--- codex 출력 ---" >&2; cat "$OUT" >&2; }
  exit "$rc"
fi

if [ ! -s "$OUT" ]; then
  echo "Codex 가 아무 출력도 내지 않았다. 리뷰로 기록하지 않는다." >&2
  exit 1
fi

echo >&2
echo "───── Codex 출력 ─────" >&2
cat "$OUT" >&2
echo "──────────────────────" >&2
echo >&2

# Codex 가 코드를 고쳤을 수 있으므로 기록은 수정 이후 상태로 남긴다.
"$ROOT/.agents/scripts/record-codex-review.sh" "$OUT" code

cat >&2 <<'NEXT'

다음(2중 검증 루프 4단계) — 이 리뷰를 그대로 믿지 마라:
  - 직접 코드를 열거나 재현해서 확인한 것만 인정한다
  - Codex 가 코드를 고쳤다면 그 수정이 옳은지 검증한다
  - Codex 가 못 보는 영역을 따로 확인한다 (실측: 놓친 3건 중 2건이 빌드와 무관한 blind spot)
  - 코드를 더 고치면 해시가 바뀌어 게이트가 다시 막는다 — 그때는 리뷰를 다시 돌려야 한다
NEXT
