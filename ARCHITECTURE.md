# ARCHITECTURE

MotiveDiet 백엔드의 최상위 구조. **왜 이렇게 생겼는지**를 적는다 — 파일 목록이 아니라 흐름과 경계.

세부 스키마는 `docs/design-docs/API.md`, 만들 순서는 `docs/exec-plans/ROADMAP-BE.md` 참고.

---

## 경계

```
        iOS 앱
          │  JWT (Authorization: Bearer)
          ▼
   ┌─────────────────┐      OAuth2 인가 코드
   │  MotiveDiet_BE  │◄──────────────────────► Google
   │  (Railway)      │      userinfo
   └────────┬────────┘
            │                                   ┌──────────┐
            ├─ JDBC ──► MySQL (Railway)         │  OpenAI  │
            │           동일 프로젝트 내부망      │  GPT-5   │
            └─ HTTPS ─────────────────────────► └──────────┘
                        (Phase 1~3, 미착수)
```

외부 의존은 셋뿐이다: **Google**(로그인), **MySQL**(영속), **OpenAI**(메시지 생성).
OpenAI 연동은 아직 코드에 없다 — Phase 1에서 `OpenAiClient` 하나로 모든 LLM 호출을 통과시킬 예정이다(벤더 하나, API 키 하나).

## 패키지 배치

```
com.example.motivediet_be
├── config/       SecurityConfig — Stateless, /api/oauth2/** 만 permitAll
├── controller/   AuthController(OAuth 흐름), TestController(인증 확인용)
├── service/      AuthService — 구글 토큰 교환 + 로그인/가입
├── jwt/          TokenProvider(발급·검증), JwtFilter(요청당 인증 주입)
├── domain/       User, Role
├── dto/          TokenDto, UserInfoDto
└── repository/   UserRepository
```

## 인증 흐름

```
GET /api/oauth2/login/google
   └─ state 생성 → HttpOnly 쿠키에 심고 → 구글 동의 화면으로 302

GET /api/oauth2/callback/google?code=&state=
   ├─ 쿠키의 state와 파라미터 state를 상수시간 비교 (불일치 → 400)
   ├─ code → 구글 access token 교환
   ├─ access token → userinfo (sub, email, name, picture)
   ├─ sub(googleId)로 User 조회 → 없으면 email로 조회해 googleId 연결 → 없으면 생성
   └─ JWT 발급 (subject = User.id)

이후 모든 요청
   └─ JwtFilter → TokenProvider.getAuthentication()
        └─ 서명 검증 후 DB에서 User 재조회 (삭제된 사용자 차단, 역할 최신화)
```

**설계상 알아둘 것:**

- **사용자 식별자는 구글 `sub`(`USER_GOOGLE_ID`)이지 email이 아니다.** email은 재사용될 수 있어서(계정 삭제 후 같은 주소 재발급) 신원으로 쓰면 계정 탈취가 가능하다. email은 표시용 속성으로만 취급한다.
- `USER_GOOGLE_ID`는 nullable + unique다. **nullable인 이유는 마이그레이션 때문**이다 — 이 컬럼 도입 전에 가입한 사용자는 값이 없고, 최초 재로그인 때 email로 찾아 연결한다. MySQL은 unique 인덱스에서 다중 NULL을 허용하므로 기존 행이 깨지지 않는다.
- **JWT가 stateless인데 요청마다 DB를 친다.** 순수 stateless라면 안 쳐야 하지만, 그러면 삭제된 사용자의 유효 토큰이 통과하고 역할 변경이 만료까지 반영되지 않는다. MVP 규모에선 정확성을 택했다. 트래픽이 늘면 재검토 대상.
- **콜백이 아직 JSON을 반환한다.** iOS는 `ASWebAuthenticationSession`으로 이 흐름을 타서 JSON을 받을 방법이 없으므로, 커스텀 URL 스킴(`motivediet://auth?token=...`) 302로 바꿔야 한다 (ROADMAP Phase 0 미완).

## 데이터 모델

현재 테이블은 `user` 하나뿐이다. Phase 1에서 `MotiveSignal`, `FoodCategory`, `FavoriteFood`, `FoodLog`가 추가된다.

**동기 원문은 저장하지 않는다.** 온보딩 자유텍스트는 `gpt-5-mini`로 구조화 신호(이벤트 유형/대상/날짜/paraphrase)만 뽑고 원문 문자열은 메서드 지역 변수로만 존재하다 폐기된다. 개인정보 요구사항과 직결된 제약이다 (PRD 5절).

## 빌드·배포

- **빌더**: Railpack (Railway 기본). `main` 푸시 시 자동 트리거
- **`.java-version`**: Java 17 강제. 없으면 빌더가 21을 깔고 toolchain 불일치로 빌드 실패
- **`railpack.json`**: 시작 커맨드 오버라이드. 빌더가 멀티모듈을 가정해 `*/build/libs/*.jar`를 찾는데 이 프로젝트는 단일 모듈이라 경로가 안 맞음
- **`schema-changes.sql`**: 미적용 DDL 누적. **머지 전에 수동 실행해야 한다** (마이그레이션 도구 없음)
- **프로필**: `SPRING_PROFILES_ACTIVE=prod`. `prod`는 `ddl-auto: validate`, `local`은 `create`
