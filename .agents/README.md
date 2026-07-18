# .agents — 하네스 내부

도구 무관 SoT. **읽을 때만 비용이 드는 문서다** — `AGENTS.md` 처럼 매 세션 로드되지 않는다.
하네스를 고칠 때만 열면 된다.

## 원칙: 컨텍스트는 제한된 예산이다

**결정적으로 검사할 수 있는 것은 컨텍스트에 넣지 않는다.** 린터·CI·게이트로 강제한다.
컨텍스트에는 기계가 판단할 수 없는 것만 둔다 — 왜 그렇게 설계했는지, 무엇과 맞바꿨는지.

| 성격 | 어디서 강제 | 컨텍스트 비용 |
|---|---|---|
| 컴파일·테스트·비밀값·스키마 정합 | CI + `quality-gate.py` | **0** |
| 일반 코딩 컨벤션 (단순함·외과적 변경) | 상위 `CLAUDE.md` (이미 자동 로드) | 중복 금지 |
| 설계 근거·도메인 지식·트레이드오프 | `.agents/rules/` (조건부 주입) | 매칭될 때만 |
| 상세 명세·긴 로그·API 레퍼런스 | 도구로 필요할 때만 가져옴 | 읽을 때만 |

**룰을 추가하기 전에 물어라: 린터나 게이트가 잡을 수 있는가?** 잡을 수 있으면 룰이 아니라 검사로 만든다.

## 구조

```
.agents/           도구 무관 SoT
├── rules/         경로 매칭으로 조건부 주입되는 설계 지식 (한 룰 60줄 이내)
├── skills/        SKILL.md 스펙. 어댑터로 심링크 (현재 비어 있음)
├── memory/        외부 시스템 포인터 인덱스 (비어 있음 — 하위 README)
├── tasks/         휘발성 상태. gitignore (하위 README)
├── mcp/           MCP 정본 → .mcp.json / ~/.codex/config.toml 로 렌더링
└── scripts/       동기화·검사
.claude/           Claude 어댑터 (settings.json = 훅·권한, skills/ = 심링크)
.codex/            Codex 어댑터 (AGENTS.md·skills/ = 심링크)
.mcp.json          Generated — 직접 고치지 말 것
```

**3분류:**

| 분류 | 처리 | 예 |
|---|---|---|
| Portable | 심링크 | `.agents/skills/` — 모든 도구가 같은 포맷으로 읽음 |
| Generated | 변환 스크립트 | MCP — Claude=JSON, Codex=TOML 로 포맷이 다름 |
| Agent-specific | 그대로 | `.claude/settings.json` — 훅·권한은 Claude 전용 개념 |

심링크는 **디렉토리 전체가 동일하고 포맷 변환이 불필요할 때만.**

## 룰 주입

`.agents/rules/*.md` 의 frontmatter `paths:` 로 적용 범위를 지정한다.

- **Claude**: `PreToolUse:Edit|Write` 훅이 자동 주입. 아무것도 안 해도 된다
- **Codex**: 동등한 훅이 없다 → `.agents/scripts/codex-rule-hint.sh <파일>` 직접 호출

둘 다 `rule-matcher.py` 하나를 공유한다 — 두 도구가 서로 다른 규칙을 보지 않게.

## 스크립트

| 스크립트 | 하는 일 | 언제 |
|---|---|---|
| `codex-review.sh "<프롬프트>"` | **Codex 교차 검증 실행 + 기록** (3단계) | 코드를 고쳤을 때 |
| `quality-gate.py` | 하드 게이트 검사 (`QUALITY_SCORE.md`) | **Stop 훅이 자동 호출** |
| `quality-gate.py --report` | 사람이 읽는 리포트 | 수동 |
| `check-review.py --verify` | 교차 검증 기록 확인 (게이트·CI 공유) | 자동 |
| `scan-secrets.py --tracked` | 비밀값 스캔 (게이트·CI 공유) | 자동 |
| `rule-matcher.py` | 경로 ↔ 룰 매칭 | 훅이 자동 호출 |
| `codex-rule-hint.sh <파일>` | Codex용 룰 조회 | Codex가 고치기 전 |
| `link-skills.sh` | 스킬 심링크 정렬·stale 정리 | 스킬 추가·삭제 후 |
| `sync-mcp.sh [--check]` | MCP 정본 → 두 도구 설정 | MCP 변경 후 |

**절차를 문서에 적지 말고 스크립트로 만들어라.** "이렇게 치라"고 적힌 명령은 지켜지는지
확인할 방법이 없다 — 실제로 Codex 실행 플래그가 README 에만 있어서, 잘못된 플래그로
돌려도 게이트가 통과시키는 상태였다. 스크립트가 실행하면 플래그가 코드가 된다.

## 2중 검증 루프

**같은 모델이 만든 것을 같은 모델이 검증하지 않는다.** 자기 채점은 후해진다.

| 단계 | 담당 |
|---|---|
| 1. 계획 | Claude + Codex |
| 2. 코드 작성 | Claude |
| 3. 1차 검증 + 수정 | Codex |
| 4. 2차 검증 · 머지 판단 | Claude |

3단계는 `.codex/README.md` 의 실행 공식을 따른다. 게이트 5번이 이 루프를 강제한다(`QUALITY_SCORE.md`).

**운영 원칙:**

- **리뷰를 시킬 때 결론을 주지 마라.** "X가 버그 같은데 맞나?"는 동조만 부른다
- **수정을 시킬 때는 제약을 알려줘라.** `AGENTS.md` 지뢰를 모르면 "고쳤다"며 프로덕션을 깨뜨린다. 리뷰의 중립성과 수정의 제약 고지는 별개다
- **샌드박스를 풀어도 4단계를 생략하지 마라.** 1회차 실측에서 Codex가 놓친 3건 중 2건(쿠키 `Secure` 누락, API 문서 미갱신)은 빌드와 무관한 blind spot이었다
- **리뷰 결과를 그대로 릴레이하지 마라.** 직접 재현·확인한 것만 통과시킨다

## MCP

정본 `.agents/mcp/servers.json` → 고친 뒤 `sync-mcp.sh`.

| 서버 | 용도 |
|---|---|
| `railway` | 배포 상태·로그·환경변수 |
| `playwright` | 브라우저 자동화. 실제로 태워봐야 검증되는 흐름용 |
| `sequential-thinking` | 다단계 추론 보조 |

`~/.codex/config.toml` 의 railway 는 관리 밖이다 — `railway setup agent` 가 전역에 먼저 넣어서 `sync-mcp.sh` 가 TOML 중복 키를 피해 건너뛴다. 동작엔 문제없다.

**서버를 추가하면 실제로 뜨는지 확인할 것.** 설정이 맞다고 기동이 보장되지 않는다:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"p","version":"1"}}}' \
  | npx @playwright/mcp@latest 2>/dev/null | head -c 300   # serverInfo 가 나오면 정상
```

## 하네스 자체를 정기적으로 점검할 것

주기적으로 확인:

```bash
wc -l AGENTS.md                      # 매 세션 로드된다. 80줄 넘으면 덜어낼 것
wc -l .agents/rules/*.md             # 한 룰 60줄 이내
```

- **매 세션 로드되는 것**(`AGENTS.md`)과 **읽을 때만 로드되는 것**(`docs/`, 이 파일)을 구분하라. 후자는 길어도 비용이 0이다. `API.md` 244줄은 문제가 아니다
- **중복을 찾아라**: `grep -rl "<키워드>" AGENTS.md QUALITY_SCORE.md SECURITY.md .agents/rules/ .codex/` — 2곳 이상 나오면 하나를 포인터로 바꾼다
- **룰이 게이트/CI와 겹치는지 보라.** 겹치면 룰을 지운다. 결정적 검사가 확률적 주입을 이긴다
- 안 쓰는 룰·스킬·스크립트는 지운다
