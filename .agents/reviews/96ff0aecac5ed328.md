---
code_hash: 96ff0aecac5ed328
stage: code
at: 2026-07-18T17:55:31+0900
reviewer: codex
---

# Codex 교차 검증 — code

이 기록은 코드 해시 `96ff0aecac5ed328` 를 검증한 것이다. 이후 코드가 바뀌면 게이트가 다시 막는다.

## Codex 출력 (원문)

### 발견된 결함

- **[P2] 두 번째 슬롯 충돌을 실제 슬롯 부족으로 잘못 반환합니다.**  
  [FavoriteFoodService.java:44](/Users/seok_hwan_/MotiveDiet/MotiveDiet/MotiveDiet_BE/src/main/java/com/example/motivediet_be/service/FavoriteFoodService.java:44)

  재현 시나리오:

  1. 사용자의 즐겨찾기가 0개인 상태에서 동일 사용자의 요청 A/B/C가 동시에 실행됩니다.
  2. 세 요청 모두 슬롯 `0`을 선택합니다.
  3. A가 저장되고 B/C는 UNIQUE 위반을 받습니다.
  4. B/C가 재조회한 뒤 모두 슬롯 `1`을 선택합니다.
  5. B는 저장되지만 C는 두 번째 UNIQUE 위반을 받습니다.
  6. C는 다시 재조회하지 않고 `FAVORITE_SLOT_FULL`을 반환합니다.

  이때 슬롯은 `0`, `1` 두 개만 사용 중이고 `2~4`가 비어 있으므로 API 계약의 “이미 5개 슬롯이 꽉 찼을 때”와 맞지 않습니다. 두 번 연속 충돌을 FULL로 간주하지 말고, 용량만큼 제한적으로 재시도하거나 마지막 충돌 후 재조회하여 실제 5개인지 구분해야 합니다.

### 나머지 검증 결과

- 무한루프는 없습니다. 반복 횟수가 2회로 고정되어 있습니다.
- `FavoriteFoodService.add()`에는 외부 `@Transactional`이 없으므로 실패한 `saveAndFlush()` 트랜잭션은 해당 저장소 호출에서 롤백됩니다. 다음 재조회/저장은 새 트랜잭션에서 실행되어 rollback-only 재사용 문제는 없습니다.
- OpenAI의 `choices`, `message`, `content`, refusal성 응답 경로는 명시적인 런타임 예외로 전환되며 온보딩 트랜잭션 전체가 롤백됩니다.
- connect 5초/read 30초 타임아웃은 정상 적용됩니다. 타임아웃은 `ResourceAccessException`으로 전파되어 일반 500이 되지만, 런타임 예외이므로 목표·동기 데이터는 함께 롤백됩니다. 새 부분 저장 결함은 없습니다.
- 엔티티 변경에 대응하는 DDL이 `schema-changes.sql`에 포함되어 있고, `application.yml`의 OpenAI 키도 `${OPENAI_API_KEY}` 플레이스홀더라 비밀값 노출은 없습니다.
- `./gradlew test --offline` 및 `git diff --check`는 통과했습니다. 다만 이번 동시 재시도와 OpenAI 실패/타임아웃을 직접 검증하는 테스트는 아직 없습니다.