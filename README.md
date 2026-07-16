# MotiveDiet Backend

동기 기반 팩폭 다이어트 코칭 앱의 백엔드. 사용자가 입력한 "다이어트 동기"를 실시간 코칭 메시지에 엮는 것이 차별화 지점이다.

Spring Boot 4.1 · Java 17 · MySQL · Spring Security(Stateless) + JWT · 배포는 Railway.

## 문서 지도

이 README는 안내판이다. 내용은 아래에 있다.

| 알고 싶은 것 | 파일 |
|---|---|
| **에이전트(Claude/Codex)로 작업한다** | [`AGENTS.md`](AGENTS.md) — 먼저 읽을 것. 지뢰와 실행 공식이 여기 있다 |
| 전체 구조·요청 흐름 | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| 제품 요구사항·결정 로그 | [`docs/product-specs/PRD.md`](docs/product-specs/PRD.md) |
| API 계약 | [`docs/design-docs/API.md`](docs/design-docs/API.md) |
| 개발 순서 | [`docs/exec-plans/ROADMAP-BE.md`](docs/exec-plans/ROADMAP-BE.md) |
| 배포 사고 기록 | [`docs/references/레일웨이_배포주의점.md`](docs/references/레일웨이_배포주의점.md) |
| 코드 품질 기준 | [`QUALITY_SCORE.md`](QUALITY_SCORE.md) |
| 보안 규칙 | [`SECURITY.md`](SECURITY.md) |

## 로컬 실행

```bash
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

`local` 프로필은 `ddl-auto: create`라 테이블을 알아서 만든다. 필요한 환경변수는 `src/main/resources/application-local.yml`의 `${...}` 플레이스홀더 참고 — **실제 값은 이 레포에 없다.**

## 주의

- `main` 푸시 = 즉시 프로덕션 배포
- 엔티티에 컬럼을 추가했다면 머지 **전에** `schema-changes.sql`을 DB에 적용해야 한다 (마이그레이션 도구 없음, prod는 `ddl-auto: validate`)

상세는 [`AGENTS.md`](AGENTS.md)의 "지뢰" 절.
