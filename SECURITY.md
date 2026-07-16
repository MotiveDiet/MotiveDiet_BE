# SECURITY

이 레포에서 협상 불가인 보안 규칙. **실제로 이 코드에서 재현 가능했던 문제**를 기준으로 적는다 — 일반론 목록이 아니다.

---

## 비밀값

- **비밀값은 Railway 환경변수에만 존재한다.** 코드·문서·커밋·로그·이슈 어디에도 실제 값을 남기지 않는다.
- `application-*.yml`은 `${ENV}` 플레이스홀더만 쓴다. 기본값을 인라인으로 넣지 않는다 (`${JWT_SECRET:default}` 금지 — 기본값이 곧 유출이다).
- 관리 대상: `DB_URL` `DB_USER_NAME` `DB_PASSWORD` `JWT_SECRET` `GOOGLE_CLIENT_ID` `GOOGLE_CLIENT_SECRET` `GOOGLE_REDIRECT_URI` `ACCESS_TOKEN_VALIDITY_IN_MILLISECONDS`
- 값이 필요하면 Railway 대시보드나 `railway variables --service MotiveDiet_BE`로 조회한다.
- **비밀값을 커밋했다면 되돌리는 것으로 끝나지 않는다.** 로테이션이 필수다 — git 히스토리와 원격에 이미 남았다고 가정할 것.

## 인증

- **사용자 식별자는 구글 `sub`(`USER_GOOGLE_ID`)다. email이 아니다.**
  email은 재사용될 수 있어(계정 삭제 후 같은 주소 재발급) 신원으로 쓰면 계정 탈취가 성립한다. email은 표시용 속성으로만 취급한다. 신규 코드에서 `findByEmail`을 신원 조회로 쓰지 말 것.
- **OAuth2 콜백은 `state`를 검증한다.**
  `state`가 없으면 공격자가 자기 인가 코드를 피해자에게 열게 해서 피해자 브라우저를 공격자 계정으로 로그인시킬 수 있다(로그인 CSRF). `state`는 `SecureRandom` 32바이트, `HttpOnly` 쿠키 보관, 상수시간 비교(`MessageDigest.isEqual`), 검증 후 즉시 폐기.
- **`Secure` 쿠키 플래그는 HTTPS 요청에만 붙인다.**
  로컬 http 개발을 깨뜨리지 않기 위해 `request.isSecure()`로 분기한다. Railway는 TLS를 엣지에서 끊으므로 `server.forward-headers-strategy: framework`가 있어야 `isSecure()`가 참을 반환한다 — 이 설정이 빠지면 프로덕션에서 `Secure`가 조용히 안 붙는다.
- **JWT는 서명 검증만으로 신뢰하지 않는다.**
  `TokenProvider.getAuthentication()`이 DB에서 사용자를 재조회한다. 안 그러면 삭제된 사용자의 미만료 토큰이 통과하고, 역할 변경이 토큰 만료까지 반영되지 않는다.
- 새 엔드포인트는 기본이 인증 필요다. `SecurityConfig`에서 `permitAll`에 추가하는 것은 **의식적인 결정**이어야 한다.

## 신뢰 경계

들어오는 값 중 다음은 전부 통제 불가 입력으로 취급한다:

| 출처 | 주의 |
|---|---|
| OAuth 콜백의 `code` / `state` | `state` 검증 전에 `code`를 교환하지 말 것 |
| 구글 `userinfo` 응답 | `verified_email`이 참인지 확인. `sub` 누락 시 거부 |
| `Authorization` 헤더의 JWT | 서명 + DB 존재 확인 둘 다 |
| 온보딩 동기 자유텍스트 | LLM 프롬프트로 들어간다. 원문은 저장하지 않는다(PRD 5절) |

## LLM 관련 (Phase 1~3)

- **동기 원문은 DB·로그 어디에도 저장하지 않는다.** 파싱 결과 구조화 필드만 남긴다. paraphrase는 원문을 복원할 수 없는 수준으로 요약한다.
- **팩폭 생성 시스템 프롬프트에 콘텐츠 가이드라인 (a)~(d)를 고정 삽입한다** (PRD 7절 3번). 자해·자살·섭식장애 소재 배제는 여기서만 담보된다 — 입력 자동 판정 가드레일은 폐기됐다(PRD 7절 2번).
- OpenAI API 키는 환경변수로만. 프롬프트에 사용자 비밀값을 넣지 않는다.

## 의존성·배포

- 새 의존성 추가는 최소화한다. 몇 줄로 되는 일에 라이브러리를 붙이지 않는다.
- `main` 푸시는 즉시 프로덕션 배포다. 검증 안 된 코드를 `main`에 올리지 않는다.
- 스키마 변경은 `schema-changes.sql`을 **머지 전에** 실행한다. 순서를 어기면 앱이 기동하지 못한다.
