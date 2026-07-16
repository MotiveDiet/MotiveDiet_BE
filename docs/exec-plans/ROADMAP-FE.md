# ROADMAP: MotiveDiet Frontend (iOS)

**기준 문서**: ../product-specs/PRD.md v0.1
**작성일**: 2026-07-13
**스택**: SwiftUI, iOS 17+ 타겟, MVVM(화면별 View + ObservableObject ViewModel), `URLSession` + `async/await`(서드파티 네트워킹 라이브러리 없음)

백엔드는 `./ROADMAP-BE.md`, 요청/응답 스키마는 `../design-docs/API.md` 참고. Phase 번호는 두 로드맵 간에 동일하게 맞춰져 있다.

각 항목은 "어떤 화면/컴포넌트를 어떤 API와 연결해서 구현하는지"를 적었다.

---

## Phase 0 — 기반 (Foundation)

- [ ] **iOS 프로젝트 초기 세팅** — Xcode SwiftUI App 템플릿, iOS 17+ 타겟, MVVM 구조로 시작
- [ ] **회원가입/로그인 화면** — "Google로 로그인" 버튼 → `ASWebAuthenticationSession`으로 `{BE}/api/oauth2/login/google` 오픈 → BE가 커스텀 URL 스킴(`motivediet://auth?token=...`)으로 리다이렉트하면 세션 콜백에서 token 추출 (BE가 이 리다이렉트를 지원하도록 수정하는 게 선행 조건, BE Phase 0 "iOS 콜백 대응" 참고)
- [ ] **토큰 저장/갱신** — 서드파티 라이브러리 없이 `Security` 프레임워크로 최소 Keychain 래퍼(`SecItemAdd`/`SecItemCopyMatching`) 작성. `APIClient`가 모든 요청에 `Authorization: Bearer {token}` 헤더를 자동으로 붙임

---

## Phase 1 — 온보딩 & 핵심 로깅 MVP

- [ ] **온보딩 플로우** — `NavigationStack` 기반 3단계 화면(목표체중 → 기한 → 동기 텍스트), 마지막 단계에서 `PATCH /api/users/me/onboarding` 한 번만 호출 (단계마다 API를 부르지 않아 이탈 시 부분 저장 문제가 없음)
- [ ] **팩폭 컨셉 opt-in 동의 화면** — 온보딩 진입 전 풀스크린 동의 화면. 동의 시 `PATCH /api/users/me/consent` 호출 후 온보딩으로 이동
- [ ] **즐겨찾기 음식 등록/편집 화면** — `GET /api/food-categories` 목록에서 최대 5개를 고르는 그리드 UI, 선택 즉시 `POST /api/favorite-foods` 호출. "슬롯 편집"에서는 `PUT`(교체)/`DELETE`(제거) 호출
- [ ] **홈 화면 (동기 칩 + 스트릭 달력 + 로깅 슬롯)** — 진입 시 `GET /api/home` 한 번 호출로 동기 신호 칩, 월~일 스트릭 줄(`weekStreak[].logged`가 `true`면 🔥, `false`면 빈 원 — 과거 미이행이든 아직 안 온 미래든 서버가 같은 `false`로 주므로 FE도 굳이 구분 안 함), "N일 연속"(`currentStreakDays`), 즐겨찾기 슬롯 5개+주간 카운트를 한 번에 그림
- [ ] **원탭 로깅** — 슬롯 탭 시 `POST /api/food-logs` 호출 후, 응답에 포함된 팩폭 메시지를 같은 화면에 바로 표시(버튼에 인라인 로딩 스피너, 별도 로딩 화면 없음). 로깅 성공 후 `weekStreak`/`currentStreakDays`가 바뀔 수 있으므로 홈 데이터를 다시 불러오거나 로컬에서 오늘 칸만 🔥로 낙관적 업데이트

---

## Phase 2 — 팩폭 코칭 엔진

- [ ] **팩폭 메시지 노출 UI** — 로깅 응답의 메시지를 바텀시트로 표시 (잠금화면 알림이 아니라 앱 내부 전용이라 push 권한 요청 자체가 필요 없음)
- [ ] **코칭 설정 화면** — `GET /api/users/me/coaching-settings`로 초기값 로드. 강도 4단계 `Picker`(OFF/MILD/MEDIUM/STRONG), "음식 빈도 팩폭"/"동기 콤보 메시지" 토글 2개, 잠금화면 노출 토글(항상 꺼짐, 서버 `lockScreenEnabled` 값 그대로 비활성 표시). 값 바뀔 때마다 `PATCH /api/users/me/coaching-settings` 즉시 호출

---

## Phase 3 — 음식 빈도 기반 펀치라인 레이어

BE가 음식 로그 1건당 메시지를 항상 하나만 반환하도록 통합했기 때문에(BE Phase 2/3 참고), FE에서 별도로 만들 UI가 없다 — Phase 2의 바텀시트가 팩폭/빈도 펀치라인/콤보를 전부 같은 필드로 받아 그대로 표시한다.

---

## Phase 4 — 출시 준비

- [ ] **전체 플로우 QA** — 온보딩 → 동의 → 로깅 → 팩폭 → 콤보 → 강도조절 순서로 수동 QA 시나리오를 문서화해 체크
- [ ] **App Store Connect 연령 등급 17+ 설정** — App Store Connect 앱 정보 > 연령 등급 설문에서 "성인 테마/모욕적 유머" 관련 항목 체크
- [ ] **iOS 출시** — App Store Connect에 빌드 업로드 및 심사 제출
