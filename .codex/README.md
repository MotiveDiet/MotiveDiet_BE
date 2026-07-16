# .codex — Codex 어댑터

이 디렉토리는 **Codex CLI 전용 어댑터**다. 지식의 원본은 여기 없다 — 전부 `.agents/`(도구 무관 SoT)에 있고, 여기엔 Codex가 찾아갈 수 있는 형태로만 노출한다.

| 항목 | 정체 | 원본 |
|---|---|---|
| `AGENTS.md` | 심링크 | `../AGENTS.md` |
| `skills/` | 심링크 | `../.agents/skills/` |

**이 디렉토리의 파일을 직접 고치지 마라.** 심링크라서 원본이 바뀐다.

## Codex 실행 공식

기본 샌드박스로는 Gradle이 돌지 않아 Codex가 자기 수정을 검증하지 못한다:

```bash
codex exec -s workspace-write \
  --add-dir ~/.gradle \
  -c sandbox_workspace_write.network_access=true \
  -C . -o <출력파일> "<프롬프트>"
```

- `--add-dir ~/.gradle` — Gradle 캐시·락이 워크스페이스 밖이라 쓰기 허용 필요
- `network_access=true` — `FileLockContentionHandler`가 **로컬 소켓**을 열어야 함. 인터넷이 필요한 게 아니므로 Gradle엔 `--offline`을 같이 줄 것
- `-o <파일>` — Codex 출력이 Claude 컨텍스트로 되돌아오며 과금되므로 파일로 받는다

## Claude와 다른 점 두 가지

**1. 룰 자동 주입이 없다.**
Claude Code는 `PreToolUse:Edit|Write` 훅이 파일 경로에 맞는 룰을 자동 주입하지만,
Codex에는 동등한 훅이 없다. 파일을 고치기 전에 직접 호출해야 한다:

```bash
.agents/scripts/codex-rule-hint.sh src/main/java/.../User.java
```

매칭 로직은 Claude 훅과 같은 `rule-matcher.py`를 공유한다 — 두 도구가 서로 다른 규칙을 보는 상황을 만들지 않기 위함이다.

**2. MCP 설정이 레포에 안 산다.**
Codex는 레포 로컬 `.codex/config.toml`을 자동 로드하지 않는다. 그래서 `.agents/scripts/sync-mcp.sh`가 `~/.codex/config.toml`(홈)에 sentinel 블록으로 끼워넣는다. 하네스에서 유일하게 portable하지 않은 경로다.

## 시작하기

```bash
.agents/scripts/link-skills.sh   # 스킬 심링크 정렬
.agents/scripts/sync-mcp.sh      # MCP 정본 → .mcp.json + ~/.codex/config.toml
```
