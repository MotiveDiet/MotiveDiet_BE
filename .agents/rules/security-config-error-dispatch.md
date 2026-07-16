---
name: security-config-error-dispatch
paths: ["**/config/SecurityConfig.java"]
---

# SecurityConfig 수정 시 ERROR 디스패치를 건드리지 마라

## 지우면 안 되는 줄

```java
.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
```

이 줄이 **`authorizeHttpRequests` 의 첫 번째 매처**여야 한다. 순서대로 평가되므로
`anyRequest().authenticated()` 뒤에 오면 무의미하다.

## 없으면 무슨 일이 생기나 (2026-07-16 실제 사고)

Spring Security 6+ 는 ERROR 디스패치도 필터링한다. Spring이 에러를 렌더링하려고 `/error` 로
포워딩하면 그 디스패치가 `anyRequest().authenticated()` 에 걸려, **진짜 상태 코드가
빈 본문 403 으로 덮인다.**

```
/api/oauth2/nonexistent              404 여야 하는데 → 403
/api/oauth2/callback/google (무파라미터)  400 여야 하는데 → 403
```

FE가 "잘못된 요청"과 "권한 없음"을 구분할 수 없게 된다.

## `requestMatchers("/error").permitAll()` 로 바꾸지 마라

그러면 `/error` 를 **직접 호출**하는 REQUEST 디스패치까지 열린다.
`dispatcherTypeMatchers(ERROR)` 는 에러 포워딩만 허용하고 직접 호출은 계속 막는다.

## 검증

유닛 테스트로는 못 잡는다 — MockMvc가 `/error` 포워딩을 재현하지 않아
`AuthControllerStateTest` 는 이 버그 내내 초록불이었다. **배포 후 curl 로 확인할 것:**

```bash
curl -s -o /dev/null -w "%{http_code}" $BASE/api/oauth2/nonexistent          # 404
curl -s -o /dev/null -w "%{http_code}" $BASE/error                            # 403 (직접 호출은 막혀야 함)
```

## 새 엔드포인트

기본이 인증 필요다. `permitAll` 에 추가하는 것은 **의식적인 결정**이어야 한다.
