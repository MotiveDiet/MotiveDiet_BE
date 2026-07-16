# ROADMAP: MotiveDiet Backend

**기준 문서**: ../PRD.md v0.1
**작성일**: 2026-07-13
**스택**: Spring Boot 4.1 (Java 17), Spring Data JPA + MySQL, Spring Security(Stateless) + jjwt, Lombok, Gson. LLM은 GPT-5 계열로 통일 — 파싱처럼 가볍고 빠른 판단이 필요한 작업은 `gpt-5-mini`, 팩폭·펀치라인처럼 톤이 중요한 생성 작업은 `gpt-5`. `OpenAiClient` 서비스 클래스 하나로 모든 LLM 호출을 처리 (벤더 하나, API 키 하나)

프론트엔드는 `../MotiveDiet_FE/ROADMAP.md` 참고. Phase 번호는 두 로드맵 간에 동일하게 맞춰져 있다. 실제 요청/응답 스키마는 `API.md` 참고 — 이 파일은 "무엇을 만드는지" 체크리스트이고, `API.md`가 "어떤 형태로 주고받는지" 명세다.

각 항목은 "무엇을, 어떤 엔티티/엔드포인트/알고리즘으로 구현하는지"를 적었다. `data.sql`은 Spring Boot가 부팅 시 자동 실행하는 초기 데이터 시드 파일을 뜻한다(별도 마이그레이션 도구 도입 안 함).

---

## Phase 0 — 기반 (Foundation)

- [x] 회원가입/로그인 — Google OAuth2 인가 코드 방식. `GET /api/oauth2/login/google`이 구글 동의 화면으로 리다이렉트 → `GET /api/oauth2/callback/google`이 `code`를 access token으로 교환 → 이메일로 `User` 조회, 없으면 생성 → JWT 발급
- [x] JWT 발급/검증 — `TokenProvider`(jjwt 0.12.6)가 서명, `JwtFilter`가 `Authorization: Bearer` 헤더를 읽어 `SecurityContext`에 세팅
- [x] SecurityConfig — Stateless 세션, `/api/oauth2/**`만 `permitAll`, 나머지는 JWT 필요
- [ ] **사용자 프로필 확장** — `User` 엔티티에 `goalWeight`(Double), `goalDate`(LocalDate) 컬럼 추가, JPA `ddl-auto`로 스키마 반영
- [ ] **iOS 콜백 대응** — 현재 `/api/oauth2/callback/google`이 `TokenDto`를 JSON으로 바로 반환하는데, iOS는 `ASWebAuthenticationSession`(웹뷰)으로 이 흐름을 타기 때문에 JSON 응답을 앱이 받을 방법이 없음. 콜백 성공 시 커스텀 URL 스킴(`motivediet://auth?token=...`)으로 302 리다이렉트하도록 `AuthController.googleCallback` 수정 필요 (FE Phase 0 로그인 화면의 전제조건)

---

## Phase 1 — 온보딩 & 핵심 로깅 MVP

- [ ] **목표+동기 온보딩 API** — `PATCH /api/users/me/onboarding` (body: `goalWeight`, `goalDate`, `motiveText`). `User` 갱신과 아래 파싱 호출을 한 트랜잭션에서 실행
- [ ] **OpenAiClient 서비스 클래스** — 이 프로젝트의 모든 LLM 호출(파싱/팩폭/펀치라인, Phase 1~3)이 공용으로 쓰는 클라이언트. API 키는 `application.yml`에 보관, HTTP 호출은 기존 `AuthService`의 Google 연동과 동일하게 `RestTemplate`으로 POST (OpenAI Chat Completions/Responses API)
- [ ] **동기 파싱 파이프라인** — `motiveText`를 `OpenAiClient`로 `gpt-5-mini`에 전달(Structured Outputs로 JSON 스키마 `{motiveType, target, eventDate, paraphrase}` 강제) → 스키마가 이미 강제되므로 별도 파싱 라이브러리 없이 응답 그대로 `MotiveSignal`(userId, motiveType enum, target String, eventDate LocalDate, paraphrase String) 테이블에 저장. `motiveText` 원본은 이 메서드의 지역 변수로만 존재하고, DB/로그 어디에도 쓰지 않은 채 메서드 종료 시 폐기
- [ ] **FoodCategory / 즐겨찾기 음식 슬롯** — `FoodCategory`(id, name, emoji, `weeklyThreshold` Int)는 `data.sql`로 고정 시드(예: 치킨 2, 햄버거 3, 라면 2, 빵 3, 술 2, 야식 2 — PRD 6.1). 이 테이블에 존재하는 것 자체가 "살찌는 음식" 화이트리스트이고, `weeklyThreshold`가 Phase 3 빈도 판정에 그대로 쓰임(tier 등급 없이 카테고리당 숫자 하나). `FavoriteFood`(userId, foodCategoryId, slotOrder 0~4)로 3~5개 슬롯 관리. `GET /api/food-categories`, `POST/PUT/DELETE /api/favorite-foods` CRUD (`API.md` 4절)
- [ ] **즐겨찾기 원탭 로깅 API** — `POST /api/food-logs {favoriteFoodId}` → `FoodLog`(userId, foodCategoryId, loggedAt=now) insert. 팩폭 결과(Phase 2)를 같은 응답 body에 동기로 포함해 왕복을 줄임
- [ ] **살찌는 음식 판정 로직** — 별도 로직 없음. `FoodLog.foodCategoryId`가 `FoodCategory`(화이트리스트)에 존재하면 그 자체로 "살찌는 음식"으로 취급
- [ ] **홈 대시보드 API** — `GET /api/home` (`API.md` 3절). 동기 신호 칩, 이번 주 월~일 로그 유무(`EXISTS` 쿼리 7번 또는 `IN` 한 방 쿼리 후 날짜별로 그룹핑), 연속일수(`currentStreakDays`), 즐겨찾기 슬롯+주간 카운트를 한 응답으로 묶음. **"불꽃" 판정 기준은 그날 `FoodLog`가 1건 이상 있는지(출석 여부)뿐 — 무엇을 먹었는지는 안 따짐**. 별도 스트릭 테이블 없이 매 요청마다 `FoodLog`에서 즉석 계산(로그 삭제/수정 시 동기화 문제를 피하려고)

---

## Phase 2 — 팩폭 코칭 엔진

**결정(2026-07-13)**: 5절 동기 연동 팩폭과 6절 음식 빈도 펀치라인은 서로 다른 API 호출이 아니라, `FoodLog` 저장 직후 실행되는 `generateCoachMessage(FoodLog)` 메서드 안에서 **gpt-5 호출 1회**로 통합한다. Phase 2에서는 이 메서드의 기본형(동기 컨텍스트까지)을 만들고, Phase 3에서 같은 메서드에 빈도 컨텍스트를 추가한다 — 별도 메서드/엔드포인트를 새로 만들지 않는다.

- [ ] **동기 연동 팩폭 생성** — `generateCoachMessage(FoodLog)` 신설. `OpenAiClient`로 `gpt-5`를 호출하며 프롬프트는 아래 규칙으로 조합: (1) 로깅된 `FoodCategory.name`은 항상 포함 (2) 콘텐츠 가이드라인 4개 규칙(아래)은 항상 포함 (3) 유효한 `MotiveSignal`이 있으면 `target`/`paraphrase`를 포함하고, `eventDate`가 오늘부터 0~14일 이내면 D-day 카운트다운도 추가로 포함 → 응답 문장을 `POST /api/food-logs` 응답에 포함
  - 콘텐츠 가이드라인(시스템 프롬프트에 고정 삽입): (a) 외모·체중·능력 인신공격 금지, 행동(식습관)에 대한 유머만 (b) 자해·자살·섭식장애 언급 금지 (c) 특정 집단 비하 금지 (d) 욕설·혐오 표현 금지
- [ ] **코칭 설정 API** — `User`에 `intensityLevel`(enum: OFF/MILD/MEDIUM/STRONG, 기본 MILD), `frequencyLayerEnabled`(Boolean, 기본 true), `motiveComboEnabled`(Boolean, 기본 true) 컬럼 추가. `GET/PATCH /api/users/me/coaching-settings` (`API.md` 6절, 화면 1d의 강도 4단계 + 메시지 레이어 토글 2개에 대응). `intensityLevel=OFF`면 `generateCoachMessage`가 LLM 호출 자체를 스킵, `frequencyLayerEnabled=false`면 빈도 컨텍스트를 프롬프트에서 제외, `motiveComboEnabled=false`면 D-day 카운트다운을 프롬프트에서 제외. 잠금화면 노출 토글은 응답에 `lockScreenEnabled: false` 고정값만 내려주고 PATCH 대상에서 제외(정책상 항상 꺼짐)
- [ ] **opt-in 동의 저장 API** — `User.consentedAt`(Timestamp, nullable) 컬럼, `PATCH /api/users/me/consent`가 `now()` 저장. `consentedAt`이 null이면 팩폭/펀치라인 관련 API가 403을 반환하도록 공통 인터셉터에서 체크

---

## Phase 3 — 음식 빈도 기반 펀치라인 레이어

**결정(2026-07-14)**: tier1/2/3 등급 구분을 없앴다. `FoodCategory.weeklyThreshold`(Phase 1에서 추가) 하나만 쓰고, 이 값 이상인 로그는 첫 도달 여부와 상관없이 **매번** 실제 누적 횟수를 프롬프트에 실어 보낸다 — 숫자가 매번 다르므로 GPT-5가 만드는 문장도 자연히 달라짐. 문구는 DB 템플릿 풀이 아니라 Phase 2의 `generateCoachMessage(FoodLog)`가 프롬프트에 빈도 컨텍스트를 추가로 얹어서 생성한다. **새 GPT 호출을 만들지 않는다** — 이미 Phase 2에서 나가는 호출 1회에 조건만 더 붙는다.

- [ ] **빈도 판정** — `FoodLog` 저장 시 `SELECT COUNT(*) FROM food_log WHERE user_id=? AND food_category_id=? AND logged_at >= NOW() - INTERVAL 7 DAY`로 이번 주 카운트 산출 → `count >= FoodCategory.weeklyThreshold`인지만 확인 (몇 번째로 넘었는지 따질 필요 없음, 매번 같은 조건 재확인)
- [ ] **`generateCoachMessage` 확장** — 위 조건이 참이면 Phase 2 프롬프트 조합 규칙에 `{음식명, 이번 주 실제 카운트}`를 추가. 거짓이면 Phase 2의 기본형(동기 컨텍스트만) 그대로 사용. 결과적으로 한 메서드가 "단독 팩폭 / 동기 참조 팩폭 / 빈도 펀치라인 / 동기+빈도 콤보" 네 가지를 프롬프트 조건 조합만으로 커버
- [ ] **잠금화면 알림 노출 금지** — 이 메시지들은 애초에 push 발송 로직을 타지 않고 `POST /api/food-logs` 응답 body로만 반환 (별도 알림 채널을 만들지 않음)

---

## Phase 4 — 콘텐츠 & 출시 준비

- [ ] **콘텐츠 가이드라인 셀프 검수** — 5절/6절 `gpt-5` 시스템 프롬프트에 반영된 (a)~(d) 규칙이 실제 출력에서 지켜지는지 샘플 로깅을 반복 실행해 수동 확인 (별도 관리자 UI/DB 관리 화면 없음 — 프롬프트 문자열 자체가 콘텐츠 가이드라인)
- [ ] **App Store 제출 체크리스트** — opt-in 동의 화면 존재 여부, 강도 조절 노출 여부, 17+ 등급 설정 여부를 제출 전 확인하는 문서화된 체크리스트(자동화 아님, 수동 확인)
