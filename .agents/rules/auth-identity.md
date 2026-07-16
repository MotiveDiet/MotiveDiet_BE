---
name: auth-identity
paths: ["**/service/AuthService.java", "**/domain/User.java", "**/jwt/*.java", "**/controller/AuthController.java", "**/repository/UserRepository.java"]
---

# 인증 신원 규칙

## 사용자 식별자는 구글 `sub` 이지 email이 아니다

- `User.googleId` (`USER_GOOGLE_ID`) 로 조회한다
- **`findByEmail` 을 신원 조회로 쓰지 마라.** email은 재사용될 수 있다 — 구글 계정 삭제 후
  같은 주소로 새 계정이 만들어지면, email 기준 조회는 새 사람에게 옛 사람의 계정을 내준다
- email은 표시용 속성으로만 취급한다
- `findByEmail` 이 유일하게 허용되는 곳: `googleId` 가 없는 기존 사용자를 최초 재로그인 때
  연결하는 마이그레이션 경로. 이미 다른 `googleId` 가 붙은 계정이면 **거부**해야 한다

## OAuth2 콜백은 `state` 를 검증한다

- `state` 없이 `code` 를 교환하면 로그인 CSRF 가 성립한다 (공격자가 자기 인가 코드를
  피해자에게 열게 해서 피해자 브라우저를 공격자 계정으로 로그인시킴)
- `SecureRandom` 32바이트 → `HttpOnly` 쿠키 → 콜백에서 **상수시간 비교**(`MessageDigest.isEqual`)
- 검증 후 즉시 쿠키 폐기
- `Secure` 플래그는 `request.isSecure()` 로 분기한다 (로컬 http 개발을 깨뜨리지 않기 위함).
  Railway는 프록시 뒤라 `server.forward-headers-strategy: framework` 가 있어야 참이 된다

## JWT는 서명 검증만으로 신뢰하지 않는다

`TokenProvider.getAuthentication()` 이 DB에서 사용자를 재조회한다. 안 그러면:
- 삭제된 사용자의 미만료 토큰이 통과한다
- 역할 변경이 토큰 만료까지 반영되지 않는다

요청마다 DB를 치는 비용은 MVP 규모에서 의식적으로 감수한 선택이다.

## 신뢰 경계

`code` / `state` / 구글 `userinfo` / `Authorization` 헤더는 전부 통제 불가 입력이다.
`userinfo` 는 `verified_email` 참 확인 + `sub` 누락 시 거부.
