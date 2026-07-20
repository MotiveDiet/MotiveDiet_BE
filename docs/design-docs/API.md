# API 명세서: MotiveDiet Backend

**기준**: 홈(1b)/팩폭 메시지(1c)/코칭 설정(1d) 목업, `../product-specs/PRD.md`, `../exec-plans/ROADMAP-BE.md`
**작성일**: 2026-07-14

이 문서는 실제 요청/응답 스키마 수준의 명세다. 구현 순서·방식은 `../exec-plans/ROADMAP-BE.md`, 기능 배경은 `../product-specs/PRD.md` 참고.

모든 엔드포인트(로그인 제외)는 `Authorization: Bearer {JWT}` 헤더가 필요하다. 인증 실패는 `401`, 미동의(`consentedAt` null) 상태에서 코칭 관련 API 호출 시 `403`으로 통일한다.

이번 스펙에서 확정한 것: **스트릭 달력의 "불꽃" 표시는 그날 음식 로그가 1건 이상 있었는지(출석 여부)로만 판정한다. 무엇을 먹었는지는 안 따진다.**  

> 목업 1c 화면 상단 칩에 남아있는 "tier 2" 표기는 이전 설계의 흔적이다. tier 등급은 이미 폐기했으므로(`../exec-plans/ROADMAP-BE.md` Phase 3, 2026-07-14 결정) 실제 구현은 "이번 주 4회"만 쓰고 tier 숫자는 노출하지 않는다.

---



## 0. 신규/변경 엔티티 요약

기존 `User`/`FoodCategory`/`FavoriteFood`/`FoodLog`/`MotiveSignal`에 이번 화면 대응을 위해 컬럼을 추가한다.


| 엔티티            | 변경                                                                                                                  |
| -------------- | ------------------------------------------------------------------------------------------------------------------- |
| `FoodCategory` | `emoji`(String) 컬럼 추가 — 슬롯/카테고리 목록에 아이콘 표시용                                                                         |
| `User`         | `frequencyLayerEnabled`(Boolean, 기본 true), `motiveComboEnabled`(Boolean, 기본 true) 컬럼 추가 — 1d 설정 화면의 "메시지 레이어" 토글 2개 |


새 엔티티는 없다. 스트릭은 `FoodLog`를 매일 단위로 집계해서 계산하고 별도 테이블에 저장하지 않는다(계산 비용이 작고, 저장하면 로그 삭제/수정 시 동기화 문제가 생기므로 즉석 계산이 더 단순함).

---



## 1. 인증

- `GET /api/oauth2/login/google` — 구글 동의 화면으로 `302`
- `GET /api/oauth2/callback/google` — 성공 시 `motivediet://auth?token={JWT}` 로 `302`. JSON 본문을 반환하지 않는다(iOS `ASWebAuthenticationSession` 이 웹뷰라 본문을 앱이 받을 수 없어, 커스텀 URL 스킴 리다이렉트가 토큰 전달 경로다). `state` 불일치는 `400`

---



## 2. 온보딩



### `PATCH /api/users/me/onboarding`

Request

```json
{
  "goalWeight": 68.0,
  "goalDate": "2026-09-01",
  "motiveText": "여자친구 생일 전까지 살 빼고 싶어요"
}
```

Response `200`

```json
{ "goalWeight": 68.0, "goalDate": "2026-09-01" }
```

내부적으로 `motiveText`는 파싱 후 폐기, `MotiveSignal` 저장(`../exec-plans/ROADMAP-BE.md` Phase 1 참고). 이 API는 응답에 원문/파싱 결과를 반환하지 않는다.

### `PATCH /api/users/me/consent`

Request: 없음 (호출 자체가 동의)
Response `200`: `{ "consentedAt": "2026-07-12T09:41:00" }`

---



## 3. 홈 대시보드 (화면 1b)



### `GET /api/home`

화면 1b를 그리는 데 필요한 모든 데이터를 한 번에 반환한다 (왕복 최소화).

Response `200`

```json
{
  "today": "2026-07-12",
  "motiveSignal": {
    "emoji": "🎂",
    "label": "여자친구 생일",
    "daysUntil": 12
  },
  "weekStreak": [
    { "date": "2026-07-06", "dayOfWeek": "MON", "logged": true },
    { "date": "2026-07-07", "dayOfWeek": "TUE", "logged": true },
    { "date": "2026-07-08", "dayOfWeek": "WED", "logged": true },
    { "date": "2026-07-09", "dayOfWeek": "THU", "logged": true },
    { "date": "2026-07-10", "dayOfWeek": "FRI", "logged": true },
    { "date": "2026-07-11", "dayOfWeek": "SAT", "logged": false },
    { "date": "2026-07-12", "dayOfWeek": "SUN", "logged": false }
  ],
  "currentStreakDays": 12,
  "favoriteFoods": [
    { "favoriteFoodId": 1, "foodCategoryId": 10, "name": "치킨", "emoji": "🍗", "weeklyCount": 3, "slotOrder": 0 },
    { "favoriteFoodId": 2, "foodCategoryId": 11, "name": "라면", "emoji": "🍜", "weeklyCount": 1, "slotOrder": 1 },
    { "favoriteFoodId": 3, "foodCategoryId": 12, "name": "빵", "emoji": "🥐", "weeklyCount": 2, "slotOrder": 2 },
    { "favoriteFoodId": 4, "foodCategoryId": 13, "name": "술", "emoji": "🍺", "weeklyCount": 0, "slotOrder": 3 },
    { "favoriteFoodId": 5, "foodCategoryId": 14, "name": "샐러드", "emoji": "🥗", "weeklyCount": 4, "slotOrder": 4 }
  ],
  "favoriteSlotCapacity": 5
}
```

- `motiveSignal`은 유효한 `MotiveSignal`이 없으면 `null` (칩 자체를 숨김)
- `weekStreak`는 이번 주 월~일 고정 7칸. `logged`는 `EXISTS(SELECT 1 FROM food_log WHERE user_id=? AND DATE(logged_at)=?)`. 오늘·미래 날짜도 로그가 없으면 그냥 `false` — 과거 미이행과 시각적으로 구분하지 않는다(목업에서 토요일(오늘)·일요일(미래)이 똑같은 빈 원으로 보이는 것과 동일). "미달성 날 표시"는 FE가 `false`일 때 아이콘만 정하면 됨
- `currentStreakDays` 계산: 오늘부터 거꾸로 날짜를 하나씩 보면서 `logged=true`인 날을 센다. 단, **오늘이 아직** `logged=false`**여도 스트릭을 끊지 않는다** — 오늘 로그가 없으면 어제부터 거꾸로 세기 시작. 그렇게 계속 `logged=true`가 이어지다가 처음 끊기는 지점에서 멈춘다
- `favoriteFoods.weeklyCount`는 `FoodCategory.weeklyThreshold`와 별개로 그냥 이번 주 카운트 그대로 노출(홈 화면 문구용, 팩폭 트리거 판정은 `POST /api/food-logs` 내부에서 별도 계산)

---



## 4. 즐겨찾기 슬롯



### `GET /api/food-categories`

슬롯 추가 시 고를 수 있는 전체 음식 카테고리 목록.

Response `200`

```json
[
  { "foodCategoryId": 10, "name": "치킨", "emoji": "🍗" },
  { "foodCategoryId": 11, "name": "라면", "emoji": "🍜" }
]
```



### `POST /api/favorite-foods`

Request: `{ "foodCategoryId": 15 }`

Response `201`: `{ "favoriteFoodId": 6, "foodCategoryId": 15, "name": "떡볶이", "emoji": "🌶️", "weeklyCount": 0, "slotOrder": 4 }`
Response `400`: 이미 5개 슬롯이 꽉 찼을 때 `{ "error": "FAVORITE_SLOT_FULL" }`, 없는 카테고리면 `{ "error": "INVALID_FOOD_CATEGORY" }`
Response `404`: 남의/없는 슬롯을 PUT/DELETE 할 때 `{ "error": "FAVORITE_NOT_FOUND" }` (POST 제외)

### `PUT /api/favorite-foods/{favoriteFoodId}`

슬롯이 가리키는 음식을 바꾼다("슬롯 편집" 화면에서 다른 음식으로 교체).

Request: `{ "foodCategoryId": 16 }`
Response `200`: 변경된 `FavoriteFood`

### `DELETE /api/favorite-foods/{favoriteFoodId}`

Response `204`

---



## 5. 음식 로깅 & 코칭 메시지 (화면 1c)



### `POST /api/food-logs`

원탭 로깅. 저장과 동시에 코칭 메시지를 생성해 같은 응답에 담아 반환한다 (`../exec-plans/ROADMAP-BE.md` Phase 2/3의 `generateCoachMessage` 참고).

> **Phase 2 구현됨(feat/#4)**: 로깅 저장과 함께 `generateCoachMessage`로 팩폭을 생성해 `coachMessage`에 담는다. 강도 `OFF`거나 생성 실패 시 `coachMessage`는 `null`. 미동의(`consentedAt` null)면 `ConsentInterceptor`가 `403 CONSENT_REQUIRED`로 막는다. 빈도(펀치라인) 컨텍스트는 Phase 3에서 같은 메서드에 추가된다.

Request: `{ "favoriteFoodId": 1 }`

Response `201` — 팩폭/펀치라인이 뜨는 경우

```json
{
  "foodLogId": 501,
  "loggedAt": "2026-07-12T13:41:00",
  "foodCategory": { "name": "치킨", "emoji": "🍗" },
  "weeklyCount": 4,
  "coachMessage": {
    "toneType": "FACTBOMB",
    "text": "치킨 벌써 4번째... 너 이러다 닭 되겠어.",
    "motiveCombo": {
      "text": "여자친구 생일 D-12인데 이럴 거야?",
      "daysUntil": 12
    }
  }
}
```

- `motiveCombo`는 유효한 동기 신호가 D-14 이내일 때만 존재, 아니면 필드 자체가 없음(`null` 아니라 생략 — 클라이언트가 `if (motiveCombo)`로만 체크하면 되게)
- `toneType`은 `FACTBOMB` | `NONE`(강도 OFF일 때 — 이 경우 `coachMessage` 자체가 `null`). 안전 톤(`SAFE`)은 PRD 7-2 폐기로 제거됨
- 목업 1c의 헤더 칩("치킨 · 이번 주 4회")은 `foodCategory.name` + `weeklyCount`로 구성. tier 숫자는 응답에 없음
- "ㅋㅋ 인정" / "오늘만 봐줘" 버튼은 이번 스펙에서 백엔드 반응 저장 API를 만들지 않는다(단순 화면 닫기 동작으로 충분, PRD에 반응 수집 요구사항 없음) — 나중에 톤 튜닝용 피드백 수집이 필요해지면 별도 엔드포인트로 추가

Response `403` — 미동의 상태: `{ "error": "CONSENT_REQUIRED" }`

---



## 6. 코칭 설정 (화면 1d)



### `GET /api/users/me/coaching-settings`

Response `200`

```json
{
  "intensityLevel": "STRONG",
  "frequencyLayerEnabled": true,
  "motiveComboEnabled": true,
  "lockScreenEnabled": false
}
```

`lockScreenEnabled`는 항상 `false`이고 응답에만 존재하는 읽기 전용 값이다(정책상 끌 수 없음, PATCH로 바꿀 수 없음 — 화면의 비활성 토글과 대응).

### `PATCH /api/users/me/coaching-settings`

Request (바꾸고 싶은 필드만 보내면 됨)

```json
{
  "intensityLevel": "MEDIUM",
  "frequencyLayerEnabled": false
}
```

Response `200`: 변경된 전체 설정 (5번 응답과 동일 형태)

- `intensityLevel`: `OFF` | `MILD` | `MEDIUM` | `STRONG`
- `intensityLevel=OFF`면 `POST /api/food-logs`가 `coachMessage`를 아예 생성하지 않음(LLM 호출 스킵)
- `frequencyLayerEnabled=false`면 `weeklyThreshold`를 넘겨도 프롬프트에 빈도 컨텍스트를 넣지 않음(단독/동기참조 팩폭만 가능)
- `motiveComboEnabled=false`면 D-14 이내여도 D-day 카운트다운 문구를 프롬프트에 넣지 않음(`motiveCombo` 필드가 응답에 안 생김)

