# AGENTS.md

MotiveDiet 백엔드에서 일하는 에이전트(Claude Code / Codex)의 진입점.

**이 파일은 상세를 담지 않는다.** 어디를 읽을지 안내하고, 밟으면 프로덕션이 죽는 지뢰만 적는다.
필요한 문서만 펼쳐 읽어라 — 처음부터 전부 읽으면 컨텍스트만 태운다.

---

## 이 프로젝트

동기 기반 팩폭 다이어트 코칭 앱의 백엔드. 사용자가 입력한 "다이어트 동기"를 실시간 코칭 메시지에 엮는 게 차별화 지점이다.

- **스택**: Spring Boot 4.1 / Java 17 / MySQL / Spring Security(Stateless) + JWT
- **프로덕션**: Railway — `main` 브랜치 푸시 시 자동 배포
- **단계**: 1인 개발 MVP. Phase 0(인증) 진행 중, Phase 1~4 미착수

## 어디를 읽을 것인가

| 알고 싶은 것 | 파일 |
|---|---|
| 전체 구조·요청 흐름·패키지 배치 | `ARCHITECTURE.md` |
| 제품 요구사항·수락 기준·결정 로그 | `docs/product-specs/PRD.md` |
| API 계약 (실제 요청/응답 스키마) | `docs/design-docs/API.md` |
| 무엇을 어떤 순서로 만드는지 | `docs/exec-plans/ROADMAP-BE.md` |
| 프론트 작업 범위 (Phase 번호 공유) | `docs/exec-plans/ROADMAP-FE.md` |
| 배포하다 터졌던 것들 + 재발 방지 | `docs/references/레일웨이_배포주의점.md` |
| 코드 품질 기준·머지 게이트 | `QUALITY_SCORE.md` |
| 보안 규칙 | `SECURITY.md` |

## 하네스 구조

지식은 **도구 무관 SoT** 한 곳에 두고, 도구별 어댑터는 그걸 가리키기만 한다.
같은 내용을 두 곳에 쓰면 반드시 드리프트한다 — 이 레포에서 이미 겪었다(README가 PRD 복사본이던 시절 결정 4·5번이 한쪽에서 누락됨).

```
AGENTS.md          루트 진입점 (지금 이 파일). 모든 도구 공통
CLAUDE.md          → @AGENTS.md 포인터
.agents/           도구 무관 SoT
├── rules/         파일 경로에 매칭돼 자동 주입되는 행동 규칙
├── skills/        SKILL.md 스펙. 어댑터로 심링크됨
├── memory/        외부 시스템 포인터 인덱스 (현재 비어 있음 — README 참고)
├── tasks/         휘발성 작업 상태 (현재 비어 있음 — README 참고)
├── mcp/           MCP 정본 → .mcp.json / ~/.codex/config.toml 로 렌더링
└── scripts/       어댑터 동기화
.claude/           Claude Code 어댑터 (settings.json = 훅·권한, skills/ = 심링크)
.codex/            Codex 어댑터 (AGENTS.md·skills/ = 심링크. .codex/README.md 참고)
.mcp.json          Generated — 직접 고치지 말 것
```

**3분류 원칙:**

| 분류 | 처리 | 예 |
|---|---|---|
| Portable | 심링크 | `.agents/skills/` → 두 어댑터 (모든 도구가 같은 포맷으로 읽음) |
| Generated | 변환 스크립트 | MCP (Claude=JSON, Codex=TOML로 포맷이 다름) |
| Agent-specific | 그대로 둠 | `.claude/settings.json` (훅·권한은 Claude 전용 개념) |

**심링크 기준: 디렉토리 전체가 동일하고 포맷 변환이 불필요할 때만.**

## 룰 자동 주입

`.agents/rules/*.md` 는 frontmatter의 `paths:` 로 적용 범위를 지정한다. 한 룰은 60줄 이내.

- **Claude Code**: `PreToolUse:Edit|Write` 훅이 수정 직전 경로에 매칭되는 룰을 자동 주입한다. 아무것도 안 해도 된다
- **Codex**: 동등한 훅이 없다. 파일을 고치기 전에 직접 호출할 것 —
  `.agents/scripts/codex-rule-hint.sh <파일경로>`

둘 다 `.agents/scripts/rule-matcher.py` 하나를 공유한다. 두 도구가 서로 다른 규칙을 보게 만들지 않기 위함이다.

## 스크립트

| 스크립트 | 하는 일 | 언제 |
|---|---|---|
| `link-skills.sh` | `.agents/skills/` → 두 어댑터 심링크 정렬, stale 정리 | 스킬 추가·삭제 후 |
| `sync-mcp.sh` | MCP 정본 → `.mcp.json` + `~/.codex/config.toml` | MCP 서버 변경 후 |
| `sync-mcp.sh --check` | 쓰지 않고 차이만 보고 (exit 1 = 동기화 필요) | 검증 |
| `rule-matcher.py` | 경로 ↔ 룰 매칭 (훅과 Codex가 공유) | 훅이 자동 호출 |
| `codex-rule-hint.sh` | Codex용 룰 조회 | Codex가 파일 고치기 전 |

## MCP 서버

정본은 `.agents/mcp/servers.json`. 고친 뒤 반드시 `sync-mcp.sh` 를 돌린다.

| 서버 | 용도 |
|---|---|
| `railway` | 배포 상태·로그·환경변수 조회 (`railway status`, `railway logs`) |
| `playwright` | 브라우저 자동화. OAuth 로그인 흐름처럼 **실제로 태워봐야 검증되는 것** 확인용 |
| `sequential-thinking` | 다단계 추론 보조 |

**`~/.codex/config.toml` 의 railway 는 관리 밖이다.** `railway setup agent` 가 전역에 넣어둔 게 먼저 있어서, `sync-mcp.sh` 가 TOML 중복 키를 피하려고 건너뛴다. 동작에는 문제 없다 — 정본으로 관리하고 싶으면 그 항목을 지우고 다시 돌리면 된다.

**서버를 추가하면 실제로 뜨는지 확인할 것.** 설정 파일이 맞다고 기동이 보장되지 않는다:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}' \
  | npx @playwright/mcp@latest 2>/dev/null | head -c 500   # serverInfo 가 나오면 정상
```

## 지뢰

밟으면 프로덕션이 죽거나 되돌리기 어렵다. 작업 전 반드시 확인할 것.

1. **엔티티에 컬럼/제약을 추가하면 DB 수동 DDL이 필요하다.**
   마이그레이션 도구가 없고 prod는 `ddl-auto: validate`다. 컬럼이 DB에 없으면 앱이 **기동조차 못 한다**.
   순서 고정: **(1) `schema-changes.sql` 실행 → (2) 그 다음 머지/푸시**. 상세는 `docs/references/레일웨이_배포주의점.md`.

2. **`main` 푸시 = 즉시 프로덕션 배포.**
   Railway는 `main`만 본다. 코드는 `feat/#<이슈번호>`에서 작업하고 확실할 때만 머지한다 — 아래 "브랜치 전략" 참고.

3. **비밀값을 파일에 쓰지 마라.**
   DB 비밀번호·JWT 시크릿·구글 클라이언트 시크릿은 Railway 환경변수에만 존재한다.
   `application-*.yml`은 `${ENV}` 플레이스홀더만 쓴다. 문서·커밋·로그에 실제 값을 남기지 말 것.

4. **Java 17 고정.** `build.gradle`이 toolchain 17을 요구하는데 Railway 빌더 기본값은 21이다.
   `.java-version` 파일이 이걸 잡아주고 있으니 지우지 말 것.

5. **단일 모듈 프로젝트다.** 빌더가 멀티모듈을 가정해 jar 경로를 틀리게 잡으므로 `railpack.json`이 시작 커맨드를 덮어쓰고 있다. 지우지 말 것.

## 브랜치 전략

**개발은 `feat/#<이슈번호>`에서. `main` 머지는 확실할 때만.**

- **모든 기능 개발은 `feat/#<이슈번호>` 브랜치에서 한다.** 코드를 `main`에 직접 커밋하지 않는다.
- **`main` 머지 = 즉시 프로덕션 배포다.** 되돌리려면 또 배포해야 한다. "일단 올리고 고치자"가 성립하지 않는다.
- 머지 전 체크:
  1. `QUALITY_SCORE.md`의 하드 게이트를 통과했는가
  2. 스키마를 바꿨다면 **`schema-changes.sql`을 DB에 먼저 적용했는가** (순서를 어기면 `validate` 실패로 앱이 기동조차 못 한다)
  3. 계약(요청/응답)을 바꿨다면 `docs/design-docs/API.md`를 갱신했는가
- 문서만 고치는 변경은 `docs/#main`으로 `main` 직접 커밋해도 된다 (빌드 결과가 같아 배포 위험이 없음).
- 브랜치는 머지 후에도 남겨둔다 — 이슈 번호가 곧 히스토리다.

**예외 — hotfix:** 이미 배포된 프로덕션의 결함을 좁게 고치는 경우 `fix/#main`으로 `main` 직접 커밋할 수 있다. 단 아래를 모두 만족할 때만:

- 변경 범위가 결함 하나에 한정된다 (같이 눈에 띈 다른 문제를 끼워 넣지 않는다)
- 스키마 변경이 없다 (있으면 DDL 선행이 필요하므로 일반 절차를 탄다)
- **배포 후 실제 프로덕션 응답으로 검증한다.** 유닛 테스트 통과는 근거가 안 된다 — 실제로 `@WebMvcTest`가 초록불인데 프로덕션이 깨져 있던 사례가 있다(아래 참고)

## 테스트가 잡지 못하는 것

`@WebMvcTest`는 MockMvc가 서블릿 컨테이너를 흉내 낼 뿐이라 **실제 디스패치 동작을 재현하지 않는다.**

실측 사례(2026-07-16): `AuthControllerStateTest`가 "state 불일치 → 400"을 검증하며 통과했지만, 프로덕션은 같은 요청에 **빈 본문 403**을 반환했다. 원인은 컨트롤러가 아니라 `SecurityConfig`였다 — Spring이 에러를 렌더링하려고 `/error`로 포워딩하는데 그 ERROR 디스패치가 필터를 다시 타면서 인증에 걸렸고, 진짜 상태 코드가 403으로 덮였다. MockMvc는 이 포워딩을 하지 않아 테스트는 계속 초록불이었다.

**그래서 배포 후 `curl`로 실제 응답을 확인하는 절차를 생략하지 마라.** 특히 상태 코드·헤더·쿠키 속성은 프로덕션에서만 진실이 드러난다 (`Secure` 플래그가 프록시 뒤에서 붙는지도 같은 이유로 로컬에선 확인 불가).

## 커밋 컨벤션

- 이모지 금지
- 형식: `<브랜치명>: <설명>`
  - 브랜치명은 `<type>/#<이슈번호>` (예: `feat/#2`, `fix/#5`)
  - `main`에 직접 커밋할 때는 브랜치명 자리에 `<type>/#main`
- 한글로 작성 (파일명이 영어이거나 영어가 자연스러운 경우는 예외)
- **경로를 지정해 커밋하라.** `git commit -m`은 스테이징된 것을 전부 쓸어담는다. 의도치 않은 파일이 섞이는 사고가 실제로 있었다. 커밋 전 `git diff --cached --stat`으로 인덱스를 확인할 것.

## 2중 검증 루프

**같은 모델이 만든 것을 같은 모델이 검증하지 않는다.** 교차 검증으로 blind spot을 제거하는 게 목적이다.

| 단계 | 담당 | 하는 일 |
|---|---|---|
| 1. 공동 계획 | Claude + Codex | 요구사항 분석 · 작업 분해 · 수락 기준 정의 |
| 2. 코드 작성 | Claude | 로컬 탐색 · 구현 · 테스트 작성 |
| 3. 1차 검증 + 수정 | Codex | 샌드박스 테스트 · 결함 수정 |
| 4. 2차 검증 | Claude | Codex 수정 결과를 로컬에서 최종 확인 · 머지 판단 |

### Codex 실행 공식

기본 샌드박스로는 **Gradle이 돌지 않는다.** 3단계에서 Codex가 자기 수정을 검증하게 하려면 아래 옵션이 필요하다 (2026-07-16 실측).

```bash
codex exec -s workspace-write \
  --add-dir ~/.gradle \
  -c sandbox_workspace_write.network_access=true \
  -C . -o <출력파일> "<프롬프트>"
```

| 옵션 | 없으면 생기는 일 |
|---|---|
| `--add-dir ~/.gradle` | Gradle 캐시·락 파일이 워크스페이스 밖이라 쓰기가 막힘 |
| `network_access=true` | Gradle의 `FileLockContentionHandler`가 **로컬 소켓**을 못 열어 `SocketException: Operation not permitted`로 죽는다. 인터넷이 필요한 게 아니라 로컬 소켓 때문이므로, Gradle에는 `--offline`을 같이 줘서 실제 외부 통신은 막을 것 |
| `-o <파일>` | Codex 출력 전문이 Claude 컨텍스트로 들어와 그만큼 과금된다. 파일로 받고 필요한 부분만 열 것 |

### 루프 운영 원칙

- **리뷰를 시킬 때는 검증자에게 결론을 주지 마라.** "내 생각엔 X가 버그인데 맞나?"라고 물으면 동조만 하고 교차 검증이 무의미해진다.
- **반대로 수정을 시킬 때는 제약을 반드시 알려줘라.** 위 "지뢰" 항목(특히 1번)을 모르면 `unique = true`를 붙여놓고 "고쳤다"고 보고하며 프로덕션을 깨뜨린다. 리뷰의 중립성과 수정의 제약 고지는 별개다.
- **샌드박스를 풀어도 4단계를 생략하지 마라.** 1회차 실측에서 Codex가 놓친 3건 중 **2건(쿠키 `Secure` 플래그 누락, API 계약 문서 미갱신)은 빌드와 무관한 순수 blind spot**이었다. 빌드를 돌릴 수 있었어도 못 잡았을 것들이다. 이게 교차 검증의 진짜 근거다.
- **리뷰 결과를 그대로 믿고 릴레이하지 마라.** 직접 코드를 열거나 재현해서 확인한 것만 통과시킨다.
