#!/usr/bin/env bash
# .agents/skills/ 를 각 도구 어댑터에 심링크로 노출한다.
# SKILL.md 는 모든 도구가 같은 포맷으로 읽으므로 변환 없이 심링크가 성립한다 (Portable).
# macOS 기본 bash 3.2 호환 — 연관배열/`mapfile` 등 4.x 문법 금지.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$ROOT/.agents/skills"

link_dir() {
  local adapter_skills="$1"
  mkdir -p "$adapter_skills"

  # stale 심링크 정리: 대상이 사라진 링크를 먼저 제거한다.
  for existing in "$adapter_skills"/*; do
    [ -e "$existing" ] || [ -L "$existing" ] || continue
    if [ -L "$existing" ] && [ ! -e "$existing" ]; then
      echo "  stale 제거: ${existing#$ROOT/}"
      rm "$existing"
    fi
  done

  local count=0
  for skill in "$SRC"/*; do
    [ -d "$skill" ] || continue
    local name; name="$(basename "$skill")"
    local dest="$adapter_skills/$name"

    if [ -L "$dest" ]; then
      rm "$dest"
    elif [ -e "$dest" ]; then
      echo "  건너뜀(실제 파일이 있음): ${dest#$ROOT/}" >&2
      continue
    fi

    # 어댑터 디렉토리 기준 상대 경로로 링크 (레포를 옮겨도 안 깨진다)
    ln -s "../../.agents/skills/$name" "$dest"
    echo "  링크: ${dest#$ROOT/} -> ../../.agents/skills/$name"
    count=$((count + 1))
  done
  echo "  ${adapter_skills#$ROOT/}: ${count}개"
}

echo "스킬 심링크 동기화"
link_dir "$ROOT/.claude/skills"
link_dir "$ROOT/.codex/skills"
echo "완료."
