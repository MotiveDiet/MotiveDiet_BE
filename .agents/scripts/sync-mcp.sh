#!/usr/bin/env bash
# .agents/mcp/servers.json (정본) → 도구별 설정으로 렌더링한다 (Generated).
# MCP 는 도구마다 포맷이 달라서 심링크가 성립하지 않는다 — 그래서 변환 스크립트다.
#
#   Claude : <repo>/.mcp.json          (JSON, 레포 로컬. 커밋됨)
#   Codex  : ~/.codex/config.toml      (TOML, 레포 로컬 설정을 자동 로드하지 않아 홈을 건드림)
#
# 사용: .agents/scripts/sync-mcp.sh [--check]
#   --check : 파일을 쓰지 않고 차이만 보고 (exit 1 = 동기화 필요)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$ROOT/.agents/mcp/servers.json"
CLAUDE_OUT="$ROOT/.mcp.json"
CODEX_OUT="${CODEX_HOME:-$HOME/.codex}/config.toml"
TAG="$(basename "$ROOT")"
BEGIN="# BEGIN $TAG managed MCP"
END="# END $TAG managed MCP"

CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1

[ -f "$SRC" ] || { echo "정본이 없다: $SRC" >&2; exit 1; }

# ── Claude: .mcp.json ────────────────────────────────────────────────
rendered_claude="$(python3 -c '
import json, sys
servers = json.load(open(sys.argv[1]))
print(json.dumps({"mcpServers": servers}, indent=2, ensure_ascii=False))
' "$SRC")"

if [ "$CHECK" = "1" ]; then
  if [ -f "$CLAUDE_OUT" ] && [ "$rendered_claude" = "$(cat "$CLAUDE_OUT")" ]; then
    echo "  .mcp.json: 최신"
  else
    echo "  .mcp.json: 동기화 필요"; exit 1
  fi
else
  printf '%s\n' "$rendered_claude" > "$CLAUDE_OUT"
  echo "  썼음: ${CLAUDE_OUT#$ROOT/}"
fi

# ── Codex: ~/.codex/config.toml 에 managed 블록 머지 ──────────────────
[ -f "$CODEX_OUT" ] || { echo "  Codex 설정 없음($CODEX_OUT) — 건너뜀"; exit 0; }

# 우리 블록 밖에 같은 서버 키가 이미 있으면 중복 키로 TOML 이 깨진다. 덮지 말고 멈춘다.
conflict="$(python3 - "$SRC" "$CODEX_OUT" "$BEGIN" "$END" <<'PY'
import json, sys, re
servers = json.load(open(sys.argv[1]))
text = open(sys.argv[2], encoding="utf-8").read()
begin, end = sys.argv[3], sys.argv[4]
# managed 블록을 들어낸 나머지에서만 검사
outside = re.sub(re.escape(begin) + r".*?" + re.escape(end), "", text, flags=re.S)
hits = [n for n in servers if re.search(r"^\[mcp_servers\.%s\]" % re.escape(n), outside, re.M)]
print(",".join(hits))
PY
)"

if [ -n "$conflict" ]; then
  echo "  ⚠️  Codex 설정에 관리 블록 밖에서 이미 정의된 서버가 있다: $conflict" >&2
  echo "     그대로 넣으면 TOML 중복 키로 Codex 설정이 깨진다." >&2
  echo "     ~/.codex/config.toml 에서 해당 [mcp_servers.*] 를 지운 뒤 다시 실행할 것." >&2
  exit 1
fi

rendered_codex="$(python3 -c '
import json, sys
servers = json.load(open(sys.argv[1]))
out = []
for name, cfg in servers.items():
    out.append("[mcp_servers.%s]" % name)
    if "command" in cfg:
        out.append("command = %s" % json.dumps(cfg["command"]))
    if "args" in cfg:
        out.append("args = %s" % json.dumps(cfg["args"]))
    if "url" in cfg:
        out.append("url = %s" % json.dumps(cfg["url"]))
    out.append("")
print("\n".join(out).rstrip())
' "$SRC")"

block="$BEGIN
$rendered_codex
$END"

current="$(python3 - "$CODEX_OUT" "$BEGIN" "$END" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
m = re.search(re.escape(sys.argv[2]) + r".*?" + re.escape(sys.argv[3]), text, re.S)
print(m.group(0) if m else "")
PY
)"

if [ "$current" = "$block" ]; then
  echo "  ~/.codex/config.toml: 최신"
  exit 0
fi

if [ "$CHECK" = "1" ]; then
  echo "  ~/.codex/config.toml: 동기화 필요"; exit 1
fi

python3 - "$CODEX_OUT" "$BEGIN" "$END" "$block" <<'PY'
import re, sys
path, begin, end, block = sys.argv[1:5]
text = open(path, encoding="utf-8").read()
pattern = re.escape(begin) + r".*?" + re.escape(end)
new = re.sub(pattern, lambda _: block, text, flags=re.S) if re.search(pattern, text, re.S) \
      else text.rstrip() + "\n\n" + block + "\n"
tmp = path + ".tmp"
open(tmp, "w", encoding="utf-8").write(new)
import os; os.replace(tmp, path)
PY
echo "  머지: $CODEX_OUT ($TAG managed 블록)"
