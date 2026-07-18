# .codex — Codex 어댑터

이 디렉토리는 **Codex CLI 전용 어댑터**다. 지식의 원본은 여기 없다 — 전부 `.agents/`(도구 무관 SoT)에 있고, 여기엔 Codex가 찾아갈 수 있는 형태로만 노출한다.

| 항목 | 정체 | 원본 |
|---|---|---|
| `AGENTS.md` | 심링크 | `../AGENTS.md` |
| `skills/` | 심링크 | `../.agents/skills/` |

**이 디렉토리의 파일을 직접 고치지 마라.** 심링크라서 원본이 바뀐다.

## Codex 실행

**직접 `codex exec` 를 치지 마라.** 스크립트를 쓴다:

```bash
.agents/scripts/codex-review.sh "<리뷰 프롬프트>"
```

플래그를 문서에 적어두면 지켜지는지 확인할 방법이 없다. 실제로 `-s read-only` 로 돌리면
Codex 가 자기 수정을 검증하지 못하는데도 게이트는 리뷰 기록의 존재만 보고 통과시킨다.
스크립트가 실행하면 플래그가 산문이 아니라 코드가 된다. 근거는 스크립트 주석에 있다.

스크립트가 하는 일: 올바른 플래그로 Codex 실행 → 출력을 파일로 수신(컨텍스트 절약)
→ `.agents/reviews/<코드해시>.md` 로 기록 → 게이트/CI 가 그 기록을 확인.

### 기본 샌드박스로는 Gradle 이 돌지 않는다

`--add-dir ~/.gradle` 없이 돌리면 Gradle 이 락 파일을 못 쓴다. 그것만 풀면 이번엔
`FileLockContentionHandler` 가 **로컬 소켓**을 못 열어 `SocketException: Operation not permitted`
로 죽는다 — 인터넷이 필요한 게 아니라 로컬 소켓 때문이라, `network_access=true` 를 주되
Gradle 에는 `--offline` 을 같이 줘서 실제 외부 통신은 막는다. (2026-07-16 실측)

**주의**: 여기서 말하는 `~/.gradle` 은 **홈 디렉토리의 Gradle 전역 캐시**(약 1GB, 이 머신의
모든 프로젝트가 공유)다. 레포 안의 `.gradle/` 과는 다른 것이고, 그건 Codex 와 무관하다 —
IntelliJ 든 CI 든 Gradle 을 돌리면 생기는 로컬 캐시이며 gitignore 돼 있다.

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
