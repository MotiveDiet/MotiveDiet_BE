---
name: schema-change-needs-ddl
paths: ["**/domain/*.java", "**/entity/*.java"]
---

# 엔티티를 바꾸면 수동 DDL이 필요하다

이 프로젝트엔 **Flyway/Liquibase 같은 마이그레이션 도구가 없다.** prod 프로필은
`hibernate.ddl-auto: validate` 라서, 엔티티에 컬럼·제약을 추가해도 **DB에 자동 반영되지 않고
오히려 validate 실패로 앱이 기동조차 못 한다.**

## 반드시 할 것

1. 엔티티를 고친다
2. 대응하는 DDL을 레포 루트 `schema-changes.sql` 에 **누적**한다
3. **머지 전에** DB에 먼저 적용한다:
   ```bash
   mysql -h <RAILWAY_TCP_PROXY_DOMAIN> -P <PORT> -u root -p<PASSWORD> railway < schema-changes.sql
   ```
4. 적용을 확인한다 (`DESCRIBE user;`, `SHOW INDEX FROM user;`)
5. 그 다음 머지/푸시

## 순서가 고정인 이유

`main` 머지 = Railway 즉시 배포. DB에 컬럼이 없으면 `validate` 가 실패해 앱이 안 뜬다.
**nullable 컬럼 추가는 현재 돌아가는 구버전 앱을 깨뜨리지 않으므로 먼저 실행해도 안전하다.**

## 마이그레이션 호환 팁

- 새 컬럼은 **nullable** 로 추가하고, 기존 행은 코드에서 나중에 채운다
  (예: `USER_GOOGLE_ID` 는 nullable + unique. 기존 사용자는 최초 재로그인 때 연결)
- MySQL은 unique 인덱스에서 **다중 NULL을 허용**하므로 기존 행이 제약을 위반하지 않는다
- `NOT NULL` 컬럼을 기존 테이블에 바로 추가하면 기존 행 때문에 실패한다

상세: `docs/references/레일웨이_배포주의점.md`
