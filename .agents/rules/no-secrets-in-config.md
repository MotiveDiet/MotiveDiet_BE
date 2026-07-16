---
name: no-secrets-in-config
paths: ["**/application*.yml", "**/application*.yaml", "**/*.properties", "**/.env*"]
---

# 설정 파일에 비밀값 금지

## 규칙

- **실제 값을 절대 쓰지 않는다.** `${ENV}` 플레이스홀더만 쓴다
- **기본값도 금지.** `${JWT_SECRET:changeme}` 처럼 쓰면 그 기본값이 곧 유출이다
- 비밀값은 Railway 환경변수에만 존재한다. 조회는 `railway variables --service MotiveDiet_BE`

## 대상 변수

`DB_URL` `DB_USER_NAME` `DB_PASSWORD` `JWT_SECRET` `GOOGLE_CLIENT_ID`
`GOOGLE_CLIENT_SECRET` `GOOGLE_REDIRECT_URI` `ACCESS_TOKEN_VALIDITY_IN_MILLISECONDS`

## 커밋했다면

되돌리는 것으로 끝나지 않는다. **로테이션이 필수**다 — git 히스토리와 원격에 이미 남았다고 가정할 것.

## 실제 사례 (2026-07-16)

`.claude/settings.local.json` 의 permissions 배열에 로컬 DB 비밀번호와 JWT 시크릿이
`Bash(export DB_PASSWORD="...")` 형태로 들어가 있었다. `.gitignore` 에서 `.claude` 를
통째로 제외하던 것을 푸는 과정에서 하마터면 org 레포에 커밋될 뻔했다.
`.claude/settings.local.json` 만 콕 집어 무시하도록 바꿔서 막았다.

**교훈**: 비밀값은 yml 뿐 아니라 **툴 설정 파일**에도 스며든다. 무시 규칙을 넓게 풀 때는
그 안에 뭐가 있는지 먼저 열어볼 것.
