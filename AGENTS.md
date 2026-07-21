# AGENTS.md

MotiveDiet 백엔드. 에이전트(Claude Code / Codex) 진입점.

**여기엔 상세가 없다.** 어디를 읽을지 안내하고, 밟으면 프로덕션이 죽는 지뢰만 적는다.
필요한 문서만 펼쳐 읽어라 — 이 파일은 매 세션 로드되므로 길어지면 그만큼 매번 태운다.

## 이 프로젝트

동기 기반 팩폭 다이어트 코칭 앱의 백엔드. 사용자가 입력한 "다이어트 동기"를 실시간 코칭 메시지에 엮는 게 차별화 지점이다.

Spring Boot 4.1 / Java 17 / MySQL / Spring Security(Stateless) + JWT.
프로덕션은 Railway — **`main` 푸시 = 즉시 배포**. 1인 개발 MVP, Phase 0(인증) 진행 중.

## 지뢰

1. **엔티티에 컬럼/제약 추가 = DB 수동 DDL.** 마이그레이션 도구가 없고 prod 는 `ddl-auto: validate` 다.
   순서 고정: **DDL 실행 → 그 다음 머지.** 어기면 앱이 기동조차 못 한다 (`docs/references/레일웨이_배포주의점.md`)
2. **`main` 푸시 = 즉시 프로덕션 배포.** 코드는 `feat/#<이슈번호>` 에서 작업한다
3. **비밀값은 Railway 환경변수에만.** yml 은 `${ENV}` 플레이스홀더만. 문서·커밋·로그에 실제 값 금지 (`SECURITY.md`)
4. **`.java-version`(Java 17)·`railpack.json`(단일모듈 시작커맨드) 지우지 말 것.** 빌더 기본값과 달라서 이 파일들이 잡아주고 있다
5. **빈 계정으로만 확인하면 못 잡는다.** 목록을 돌려주는 API는 데이터가 0건이면 루프·변환이 아예 실행되지 않아 멀쩡해 보인다.
   집계/조회 API를 손댔으면 **데이터가 1건 이상인 계정으로 반드시 다시 호출**할 것 (2026-07-21 `GET /api/home` 500 장애의 원인)
6. **`@Query` 에서 `CAST(x AS date)` 를 `List<LocalDate>` 로 받지 말 것.** Hibernate 가 `java.sql.Date` 를 돌려주고
   Spring Data 가 변환하지 못해 `ConverterNotFoundException` → 500. 시각을 그대로 받아 Java 에서 `toLocalDate()` 로 바꾼다

## 어디를 읽을 것인가

| 알고 싶은 것 | 파일 |
|---|---|
| 전체 구조·요청 흐름·설계 근거 | `ARCHITECTURE.md` |
| 제품 요구사항·결정 로그 | `docs/product-specs/PRD.md` |
| API 계약 (요청/응답 스키마) | `docs/design-docs/API.md` |
| 무엇을 어떤 순서로 만드는지 | `docs/exec-plans/ROADMAP-BE.md` · `docs/exec-plans/ROADMAP-FE.md` |
| 배포하다 터졌던 것들 | `docs/references/레일웨이_배포주의점.md` |
| 머지 게이트·바이패스 전환 조건 | `QUALITY_SCORE.md` |
| 보안 규칙 | `SECURITY.md` |
| **하네스 내부** (룰·스킬·MCP·스크립트·검증 루프) | `.agents/README.md` |
| **Codex 실행 공식** | `.codex/README.md` |

## 브랜치

**개발은 `feat/#<이슈번호>` 에서. `main` 머지는 확실할 때만** — 머지 = 즉시 배포라 "일단 올리고 고치자"가 성립하지 않는다.

- 머지 전: `QUALITY_SCORE.md` 게이트 통과 · 스키마 바꿨으면 DDL 선행 · 계약 바꿨으면 `docs/design-docs/API.md` 갱신
- 문서만 고치는 변경은 `docs/#main` 으로 `main` 직접 커밋해도 된다 (빌드 결과가 같아 배포 위험 없음)
- **hotfix 예외**: 배포된 결함을 좁게 고칠 때 `fix/#main` 직접 커밋 가능. 단 **결함 하나로 범위 한정 + 스키마 변경 없음 + 배포 후 실제 프로덕션 응답으로 검증** 을 모두 만족할 때만
- 브랜치는 머지 후에도 남긴다 — 이슈 번호가 히스토리다

## 커밋

- 이모지 금지. 한글로 작성 (파일명이 영어이거나 영어가 자연스러운 경우는 예외)
- 형식: `<type>/#<이슈번호>: <설명>`. `main` 직접 커밋은 `<type>/#main`
- **경로를 지정해 커밋하라.** `git commit -m` 은 스테이징된 걸 전부 쓸어담는다 — 의도치 않은 파일이 섞이는 사고가 실제로 있었다. 커밋 전 `git diff --cached --stat` 으로 인덱스를 확인할 것
- **내부 은어·불투명한 식별자를 제목에 쓰지 마라.** 리뷰 기록 해시(`db478d67 — 머지 승인`), 하네스 용어, 세션 안에서만 통하는 축약은 몇 달 뒤엔 아무 뜻도 없다. 무엇이 왜 바뀌었는지를 그 자체로 읽히게 쓰고, 해시 같은 건 꼭 필요하면 본문에서 한 줄로 풀어 설명할 것

## 자동으로 도는 것

- **룰 주입** — 파일을 열면 `PreToolUse` 훅이 그 경로에 걸린 룰을 자동으로 붙인다 (Claude 한정. Codex 는 `.codex/README.md` 참고)
- **품질 게이트** — 턴을 끝내려 하면 `Stop` 훅이 검사하고, 미통과면 막는다. 미완성 결과물이 사용자에게 도달하지 않는다

동작 원리와 스크립트는 `.agents/README.md`.
