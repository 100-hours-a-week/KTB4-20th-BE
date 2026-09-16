# PlanIt 설문 기반 일정 생성 API 명세

## 1. 문서 상태

- 공통 작성 규약은 [`API_SPEC_GUIDELINES.md`](./API_SPEC_GUIDELINES.md)를 따른다.
- 이 문서에서 `APPROVED`로 표시한 계약만 확정된 계약이다.
- 설문 문항의 표시 순서는 현재 보류 상태이며 관련 API를 확정할 때 별도로 결정한다.

### 공통 설문 점수 정책

- 모든 취향 문항은 화면 진입 시 `3`으로 선택된 상태다.
- 제출 요청에는 모든 문항의 최종 선택값을 포함하며 허용 점수는 정수 `1~5`다.
- 서버는 선택값을 가공하지 않고 `survey_answers.score`에 그대로 저장한다.
- 그룹 결과는 0~100%로 환산하지 않는다. 제출 완료된 활성 멤버의 원본 점수를 카테고리별로 산술 평균하고 `1.0~5.0` 범위의 소수 첫째 자리로 반올림해 제공한다.
- 제출 완료된 설문은 재제출할 수 있으며 기존 답변, 제외 카테고리와 꼭 가고 싶은 장소 전체를 원자적으로 교체한다.
- 재제출 결과는 최신 설문 집계에 즉시 반영한다.
- 이미 실행 중인 AI 생성 작업은 시작 시점의 불변 설문 스냅샷만 사용하므로 생성 도중 재제출된 내용은 해당 작업 결과에 반영하지 않는다.
- 최초 제출은 `survey_deadline_at`까지 허용한다.
- 최초 제출을 완료한 활성 멤버의 재제출은 설문 마감이나 AI 생성 상태와 관계없이 서울 날짜 기준 여행 시작 전까지만 허용한다.
- 마감 전에 제출하지 않은 멤버는 마감 후 재제출 규칙으로 새로 제출할 수 없다.

## 2. 선택 장소 저장

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/regions/{regionId}/places` |
| 설명 | Google Places 검색 결과에서 사용자가 클릭한 장소 하나만 저장하고 내부 장소 ID를 반환한다. |
| 인증 | Access Token 필수 |
| 인가 | 로그인 사용자 |
| 멱등성 | `Idempotency-Key` 필수 |

### 요청

```http
POST /api/regions/123/places
Authorization: Bearer {accessToken}
Idempotency-Key: {uuidV7}
Content-Type: application/json
```

```json
{
  "googlePlaceId": "ChIJ_PLANIT_EXAMPLE",
  "name": "해운대 카페",
  "categoryName": "음식점 > 카페",
  "address": "부산광역시 해운대구 우동 123",
  "roadAddress": "부산광역시 해운대구 해운대로 123",
  "longitude": 129.1585,
  "latitude": 35.1587,
  "phone": "051-000-0000",
  "placeUrl": "https://www.google.com/maps/place/?q=place_id:ChIJ_PLANIT_EXAMPLE"
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `googlePlaceId` | string | Y | 1~100자 | Google Places 장소 식별값 |
| `name` | string | Y | 앞뒤 공백 제거 후 1~200자 | 장소명 |
| `categoryName` | string | N | 최대 255자 | Google Places 장소 카테고리 |
| `address` | string | N | 최대 255자 | 지번 주소 |
| `roadAddress` | string | N | 최대 255자 | 도로명 주소 |
| `longitude` | number | Y | -180 이상 180 이하 | Google Places 장소 경도 |
| `latitude` | number | Y | -90 이상 90 이하 | Google Places 장소 위도 |
| `phone` | string | N | 최대 50자 | 전화번호 |
| `placeUrl` | string(URL) | N | 최대 2083자 | Google Maps 장소 URL |

별도 `placeCandidateToken`은 사용하지 않는다.

### 성공 응답

신규 저장과 기존 장소 조회를 클라이언트가 구분할 필요가 없으므로 모두 `200 OK`를 사용한다.

```json
{
  "code": "PLACE_RESOLVED",
  "message": "선택한 장소를 확인했습니다.",
  "data": {
    "placeId": "5001",
    "regionId": "123",
    "googlePlaceId": "ChIJ_PLANIT_EXAMPLE",
    "name": "해운대 카페",
    "categoryName": "음식점 > 카페",
    "address": "부산광역시 해운대구 우동 123",
    "roadAddress": "부산광역시 해운대구 해운대로 123",
    "longitude": 129.1585,
    "latitude": 35.1587,
    "phone": "051-000-0000",
    "placeUrl": "https://www.google.com/maps/place/?q=place_id:ChIJ_PLANIT_EXAMPLE"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `PLACE_RESOLVED` | 신규 장소 저장 또는 기존 내부 장소 조회 성공 |
| `400 Bad Request` | `INVALID_REQUEST` | 필수값, 길이, 좌표 또는 URL 형식이 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `REGION_NOT_FOUND` | `regionId`에 해당하는 서비스 여행 지역이 없음 |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | 같은 Key를 다른 요청 내용에 사용함 |
| `409 Conflict` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 Key의 요청이 아직 처리 중임 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 서버는 `(regionId, googlePlaceId)`가 같은 장소가 있으면 새 행을 만들지 않고 표시 정보를 최신 요청값으로 갱신한 뒤 기존 `placeId`를 반환한다.
- 같은 Google Places 장소가 다른 여행 지역 검색에서 선택되면 지역별 검색 문맥을 유지하기 위해 별도 `places` 행으로 관리할 수 있다.
- 검색 결과 전체가 아니라 사용자가 클릭한 장소만 저장한다.
- 서버는 허용 필드와 형식만 검증한다. 후보 Token이나 서버 측 검색 세션을 사용하지 않으므로 요청값이 직전 Google Places 검색 응답과 동일한지는 보장하지 않는다.
- `placeId`와 `regionId`는 BIGINT PK이므로 JSON에서 10진수 문자열로 반환한다.

## 3. 내 설문 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}/survey` |
| 설명 | 현재 여행 멤버의 설문 답변과 희망 장소를 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 활성 멤버 본인 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "SURVEY_RETRIEVED",
  "message": "설문을 조회했습니다.",
  "data": {
    "tripId": "1001",
    "status": "SUBMITTED",
    "answers": [
      {
        "questionId": "10",
        "score": 4
      }
    ],
    "excludedCategories": [
      {
        "categoryId": "101",
        "code": "SPICY_FOOD",
        "name": "매운 음식"
      },
      {
        "categoryId": "104",
        "code": "LONG_WALK",
        "name": "장거리 도보"
      }
    ],
    "mustVisitPlaces": [
      {
        "placeId": "5001",
        "name": "해운대 카페",
        "categoryName": "음식점 > 카페"
      }
    ],
    "submittedAt": "2026-09-07T17:10:00.123456+09:00",
    "canInitialSubmit": false,
    "canResubmit": true
  }
}
```

설문을 아직 제출하지 않았으면 `status`는 `DRAFT`, `submittedAt`은 `null`, `excludedCategories`와 `mustVisitPlaces`는 빈 배열이다. `answers`에는 현재 문항별 초기 점수 `3`을 반환한다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `SURVEY_RETRIEVED` | 설문 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 해당 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `canInitialSubmit`은 미제출 상태이고 현재 시각이 `survey_deadline_at` 이하일 때만 `true`다.
- `canResubmit`은 제출 완료 상태이고 서울 날짜가 `startDate`보다 이를 때만 `true`다.
- 저장된 개인 점수는 정규화하지 않고 `1~5` 원본 값으로 반환한다.
- 문항 표시 순서는 보류 중이므로 이 응답의 `answers` 배열 순서 계약도 아직 확정하지 않는다.

## 4. 내 설문 제출·수정

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/trips/{tripId}/survey` |
| 설명 | 현재 사용자의 전체 답변과 희망 장소를 최초 제출하거나 재제출한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 활성 멤버 본인 |
| 멱등성 | 전체 상태를 교체하는 PUT, 별도 Key 불필요 |

### 요청

```http
PUT /api/trips/1001/survey
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "answers": [
    {
      "questionId": "10",
      "score": 4
    },
    {
      "questionId": "11",
      "score": 3
    }
  ],
  "excludedCategoryIds": ["101", "104"],
  "mustVisitPlaceIds": ["5001", "5002"]
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `answers` | array | Y | 현재 전체 문항을 누락·중복 없이 한 번씩 포함 | 최종 5점 척도 답변 |
| `answers[].questionId` | string | Y | BIGINT 10진수 문자열 | 문항 ID |
| `answers[].score` | integer | Y | `1~5` | 가공 없이 저장할 선택값 |
| `excludedCategoryIds` | string[] | Y | 0개 이상, 중복 불가 | 사전 등록된 일정 제외 카테고리 ID 목록 |
| `mustVisitPlaceIds` | string[] | Y | 0~10개, 중복 불가 | 선택 장소 저장 API에서 받은 꼭 가고 싶은 장소 ID 목록 |

### 성공 응답

```json
{
  "code": "SURVEY_SAVED",
  "message": "설문을 저장했습니다.",
  "data": {
    "tripId": "1001",
    "status": "SUBMITTED",
    "submittedAt": "2026-09-07T17:20:00.123456+09:00",
    "answers": [
      {
        "questionId": "10",
        "score": 4
      },
      {
        "questionId": "11",
        "score": 3
      }
    ],
    "excludedCategoryIds": ["101", "104"],
    "mustVisitPlaceIds": ["5001", "5002"]
  }
}
```

최초 제출과 재제출 모두 동일하게 `200 OK`, `SURVEY_SAVED`를 반환한다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `SURVEY_SAVED` | 최초 제출 또는 재제출 성공 |
| `400 Bad Request` | `INVALID_REQUEST` | 문항 누락·중복, 잘못된 점수, 제외 카테고리·장소 중복 또는 장소 개수 위반 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 해당 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `404 Not Found` | `EXCLUSION_CATEGORY_NOT_FOUND` | 존재하지 않는 제외 카테고리 ID가 포함됨 |
| `404 Not Found` | `PLACE_NOT_FOUND` | `mustVisitPlaceIds` 중 저장된 장소가 없음 |
| `409 Conflict` | `PLACE_REGION_MISMATCH` | 선택 장소의 `regionId`가 여행방 지역과 다름 |
| `409 Conflict` | `SURVEY_SUBMISSION_CLOSED` | 미제출 상태에서 설문 마감 시각이 지남 |
| `409 Conflict` | `SURVEY_RESUBMISSION_CLOSED` | 제출 완료 상태에서 서울 날짜 기준 여행이 시작됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 미제출 설문은 현재 시각이 `survey_deadline_at` 이하일 때만 최초 제출할 수 있다.
- 이미 제출한 설문은 설문 마감과 AI 생성 상태에 관계없이 서울 날짜가 `startDate`보다 이를 때 재제출할 수 있다.
- 재제출은 기존 `survey_answers`, `survey_excluded_categories`와 `survey_place_preferences` 전체를 새 요청값으로 교체한다.
- 답변·제외 카테고리·꼭 가고 싶은 장소 교체와 `submitted_at` 갱신은 하나의 트랜잭션으로 처리하며 실패하면 이전 제출 결과를 유지한다.
- 재제출 성공 직후 최신 그룹 취향 집계를 다시 계산한다.
- 실행 중인 AI 작업의 `schedule_generation_snapshots`와 생성 결과는 변경하지 않는다.

## 5. FastAPI 일정 생성 입력

이 항목은 사용자용 API가 아니라 Spring 서버가 AI 일정 생성 작업을 시작할 때 FastAPI 서버로 전달해야 하는 내부 입력 계약이다. 실제 내부 URL과 인증 방식은 배포 구조를 확정할 때 정한다.

```json
{
  "generationJobId": "4001",
  "trip": {
    "regionId": "123",
    "startDate": "2026-09-12",
    "endDate": "2026-09-14"
  },
  "surveyInputs": [
    {
      "surveyId": "6001",
      "preferenceAnswers": [
        {
          "categoryCode": "FOOD",
          "score": 4
        }
      ],
      "excludedCategoryCodes": ["SPICY_FOOD", "LONG_WALK"],
      "mustVisitPlaces": [
        {
          "placeId": "5001",
          "googlePlaceId": "ChIJ_PLANIT_EXAMPLE",
          "name": "해운대 카페",
          "longitude": 129.1585,
          "latitude": 35.1587
        }
      ]
    }
  ]
}
```

처리 규칙:

- Spring 서버가 제출 완료된 활성 멤버들의 설문을 기준으로 입력을 생성하며 클라이언트는 FastAPI를 직접 호출하지 않는다.
- `surveyInputs`에는 제출 완료된 활성 멤버별 설문 입력을 포함한다. 사용자 식별정보 대신 내부 `surveyId`로 입력 단위를 구분한다.
- `preferenceAnswers[].score`는 `survey_answers.score`의 `1~5` 원본 값을 가공하지 않고 전달한다.
- `excludedCategoryCodes`에는 해당 설문에서 선택한 제외 카테고리 코드를, `mustVisitPlaces`에는 `survey_place_preferences`에 연결된 꼭 가고 싶은 장소를 포함한다.
- 각 설문 입력은 작업 시작 시 `answers_snapshot`, `excluded_categories_snapshot`, `place_preferences_snapshot`에 고정한다.
- AI 생성 중 설문이 재제출돼도 현재 작업의 입력 payload와 결과는 변경하지 않는다.
- Spring 서버는 장소를 추천하거나 제외 카테고리와 꼭 가고 싶은 장소의 우선순위를 판단하지 않는다.
- FastAPI가 장소 추천, 포함·제외 판단, 일정·경로 구성과 포함하지 못한 장소의 사유 생성을 전담한다.
- FastAPI는 이동수단과 소요 시간을 고려하지 않고 장소 좌표 간 거리를 기준으로 전체 이동거리가 짧은 방문 순서를 계산한다.
- AI 생성 일정에서는 음식점끼리 또는 카페끼리 연속 배치하지 않으며, `음식점 → 카페 → 음식점`과 `카페 → 음식점 → 카페` 순서도 만들지 않는다.
- AI가 위 배치 조건을 만족하는 장소와 경로를 반환한다고 가정한다. 장소 대체와 생성 가능 여부 판단은 FastAPI 책임이며 Spring 서버는 해당 정책을 다시 계산하지 않는다.
- Spring 서버는 입력 검증·스냅샷 저장, 작업 상태 관리, FastAPI 결과의 저장과 사용자 API 제공만 담당한다.

### 작업 재시도와 스냅샷

- FastAPI 통신 오류 등 일시적 실패를 동일 작업 안에서 자동 재시도할 때는 작업 최초 생성 시점의 스냅샷을 그대로 사용한다.
- 동일 작업의 자동 재시도 중 새 설문 제출·재제출이 발생해도 입력을 다시 만들지 않는다.
- 자동 재시도가 모두 실패하면 해당 작업 상태를 최종 `FAILED`로 기록한다.
- `FAILED` 이후 방장이 다시 생성 요청을 보내면 기존 작업을 되살리지 않고 새로운 `schedule_generation_job`을 생성한다.
- 새 작업은 방장의 재요청 시점에 제출 완료된 활성 멤버의 최신 설문으로 새로운 스냅샷을 만든다.
- 이전 실패 작업과 그 스냅샷은 장애 분석과 이력 확인을 위해 유지한다.
- 최종 `FAILED` 작업은 사용자 재생성 횟수를 차감하지 않는다. 동일한 `generation_sequence`를 목표로 새 작업을 요청할 수 있다.

### 사용자 재생성과 후보 보관

- 최초 성공 결과는 `generation_sequence=1`이며 방장은 일정 확정 전에 성공한 결과를 기준으로 최대 3회까지 재생성할 수 있다. 성공한 일반 일정 결과는 여행방별 최대 4개다.
- 같은 작업 안의 시스템 자동 재시도와 날씨 기반 `WEATHER_REPLAN` 작업은 사용자 재생성 3회에 포함하지 않는다.
- 사용자 재생성은 실행 중인 다른 일정 생성 작업과 `ACTIVE` 확정 일정이 없고 서울 날짜 기준 여행 시작 전일 때만 허용한다.
- 일정이 한 번 확정되면 일반 사용자 재생성을 더 이상 허용하지 않는다. 확정 일정의 장소 추가·삭제와 D-1 날씨 기반 `WEATHER_REPLAN`은 별도 정책이므로 계속 허용한다.
- 새 작업이 성공하기 전까지 가장 최근에 성공한 후보를 유지한다.
- 새 작업이 성공하면 이전 작업에서 선택되지 않은 후보 일정과 그 하위 Day·장소·이동 구간은 삭제한다. 작업과 입력 스냅샷 이력은 유지한다.

### 일정 변경 가능 시점

- 방장은 서울 시각 기준 여행 시작 전날 `23:59:59.999999`까지만 다른 후보를 확정하거나 확정 일정의 장소를 추가·삭제할 수 있다.
- 여행 시작일 `00:00:00`부터는 후보 재확정과 장소 추가·삭제를 허용하지 않는다.
- 확정 일정의 장소 추가·삭제는 사용자가 방문 경로를 직접 커스텀하기 위한 기능이다. FastAPI를 호출하거나 AI가 장소를 추천·재배치하지 않는다.
- 장소는 선택한 Day의 처음, 두 장소 사이 또는 마지막에 삽입할 수 있다. 별도 순서 변경과 Day 간 장소 이동 기능은 제공하지 않는다.
- 수동 추가·삭제에는 음식점·카페 배치 제약을 적용하지 않는다.
- 변경된 인접 구간은 백엔드가 장소 좌표 간 거리를 다시 계산한다. 이동수단과 소요 시간은 고려하지 않으며 방문 순서도 최적화하지 않는다.
- 날씨 변경 선택에 방장이 응답하지 않으면 여행 시작 시 시스템이 기존 확정 일정을 유지하는 `KEEP` 결정을 기록한다.
- 시스템 자동 `KEEP` 결정의 `decided_by_member_id`는 `NULL`이며 자동으로 대체 일정을 생성하거나 적용하지 않는다.
- 방장이 `REGENERATE`를 선택하면 FastAPI가 날씨 기반 대체 일정을 생성한다. 생성 성공 시 별도의 재확인 없이 모든 멤버에게 보이는 현재 일정으로 자동 반영하고, 실패하면 기존 `ACTIVE` 일정을 유지한다.

## 6. AI 일정 생성 시작

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/trips/{tripId}/schedule-generation-jobs` |
| 설명 | 최신 제출 설문을 고정하고 비동기 AI 일정 생성 작업을 시작한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 현재 방장 |
| 멱등성 | `Idempotency-Key` 필수 |

request body는 사용하지 않는다.

### 성공 응답

```http
HTTP/1.1 202 Accepted
Location: /api/schedule-generation-jobs/4001
```

```json
{
  "code": "SCHEDULE_GENERATION_ACCEPTED",
  "message": "일정 생성을 시작했습니다.",
  "data": {
    "jobId": "4001",
    "tripId": "1001",
    "jobType": "GENERAL",
    "generationSequence": 2,
    "status": "QUEUED",
    "stage": "GATHERING_PREFERENCES",
    "remainingRegenerations": 2,
    "requestedAt": "2026-09-07T18:00:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `202 Accepted` | `SCHEDULE_GENERATION_ACCEPTED` | 작업·스냅샷 생성 및 비동기 처리 접수 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_HOST_REQUIRED` | 현재 방장이 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `409 Conflict` | `SCHEDULE_SURVEY_REQUIRED` | 제출 완료된 활성 멤버 설문이 하나도 없음 |
| `409 Conflict` | `SCHEDULE_GENERATION_NOT_READY` | 설문 마감 전이고 모든 활성 멤버가 제출하지 않음 |
| `409 Conflict` | `SCHEDULE_GENERATION_IN_PROGRESS` | 같은 여행방의 생성 작업이 이미 실행 중임 |
| `409 Conflict` | `SCHEDULE_ALREADY_CONFIRMED` | 일정이 이미 확정되어 일반 재생성을 요청할 수 없음 |
| `409 Conflict` | `SCHEDULE_GENERATION_LIMIT_EXCEEDED` | 사용자 재생성 3회를 모두 사용함 |
| `409 Conflict` | `SCHEDULE_CHANGE_NOT_ALLOWED` | 서울 날짜 기준 여행이 이미 시작됨 |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | 같은 Key를 다른 요청 내용에 사용함 |
| `409 Conflict` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 Key의 요청이 아직 처리 중임 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 모든 활성 멤버가 제출했거나 `survey_deadline_at`이 지난 경우에만 시작할 수 있으며, 두 경우 모두 제출 완료 설문이 최소 하나는 있어야 한다.
- `QUEUED` 또는 `RUNNING` 작업이 있으면 새 작업을 만들지 않는다.
- 최초 성공 결과는 `generationSequence=1`, 사용자 재생성 성공 결과는 `2~4`다. 최종 실패한 작업은 횟수를 차감하지 않으며 다음 요청에서 같은 `generationSequence`를 다시 사용한다.
- 실행 중인 사용자 재생성 작업에는 한 자리를 임시 예약해 `remainingRegenerations`를 표시하고, 최종 실패하면 해당 횟수를 복구한다.
- `ACTIVE` 일정이 존재하면 일반 생성 작업을 새로 만들지 않는다. 날씨 기반 대체 일정 작업은 이 제한의 예외다.
- 작업, 제출 완료된 활성 멤버별 불변 스냅샷과 멱등성 결과를 하나의 트랜잭션으로 저장한 뒤 FastAPI 비동기 처리를 요청한다.
- AI 생성 작업은 사용자 API로 취소할 수 없다.

## 7. AI 일정 생성 상태 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/schedule-generation-jobs/{jobId}` |
| 설명 | 새로고침·재접속 시 일정 생성 상태를 복구한다. |
| 인증 | Access Token 필수 |
| 인가 | 작업 대상 여행방의 활성 멤버 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "SCHEDULE_GENERATION_JOB_RETRIEVED",
  "message": "일정 생성 상태를 조회했습니다.",
  "data": {
    "jobId": "4001",
    "tripId": "1001",
    "jobType": "GENERAL",
    "generationSequence": 2,
    "status": "RUNNING",
    "stage": "FINDING_PLACES",
    "attemptCount": 1,
    "candidateCount": 0,
    "remainingRegenerations": 2,
    "startedAt": "2026-09-07T18:00:01.123456+09:00",
    "finishedAt": null
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `SCHEDULE_GENERATION_JOB_RETRIEVED` | 상태 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 작업 대상 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `SCHEDULE_GENERATION_JOB_NOT_FOUND` | 작업이 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 상태는 `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED` 중 하나이며 취소 상태는 제공하지 않는다.
- `jobType`은 최초 생성과 사용자 재생성을 모두 나타내는 `GENERAL`, 날씨 기반 대체 생성을 나타내는 `WEATHER_REPLAN` 중 하나다. 일반 생성의 최초·재생성 구분은 `generationSequence`로 판단한다.
- 진행 단계는 `GATHERING_PREFERENCES`, `FINDING_PLACES`, `OPTIMIZING_ROUTE` 순서다.
- 일반 생성 작업은 `SUCCEEDED`일 때 `candidateCount=3`, `FAILED`일 때 `candidateCount=0`이다. 세 후보가 모두 유효하지 않으면 전체 작업을 실패 처리하고 일부 후보를 공개하지 않는다.
- `WEATHER_REPLAN` 작업은 `generationSequence=null`, `remainingRegenerations=null`이다. 여행 시작 전에 정상 완료되면 대체 일정 하나를 자동 적용하고 `candidateCount=1`을 반환한다. 여행 시작 후 완료되면 적용 기한 만료로 `FAILED`, `candidateCount=0`이 된다.
- `attemptCount`는 동일 작업 안의 내부 자동 실행 횟수로 사용자 재생성 한도와 무관하다.
- 사용자 재생성 작업이 실행 중이면 해당 횟수를 임시 예약해 `remainingRegenerations`에 반영한다. 작업이 `FAILED`가 되면 예약을 복구하고, `SUCCEEDED`일 때만 최종 차감한다.
- 내부 예외, FastAPI 응답 원문과 스택 트레이스는 노출하지 않는다. 실패 화면에는 공통 `SCHEDULE_GENERATION_FAILED` 안내를 사용한다.

## 8. 후보 일정 목록 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/schedule-generation-jobs/{jobId}/candidates` |
| 설명 | 가장 최근에 성공한 일반 생성 작업의 후보 일정 3개를 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 작업 대상 여행방의 활성 멤버 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "SCHEDULE_CANDIDATES_RETRIEVED",
  "message": "후보 일정을 조회했습니다.",
  "data": {
    "jobId": "4001",
    "candidates": [
      {
        "scheduleId": "7001",
        "strategy": "SHORTEST",
        "summary": "이동거리를 줄인 일정",
        "totalDistanceMeters": 12500,
        "days": [
          {
            "dayId": "7101",
            "dayNumber": 1,
            "date": "2026-09-12",
            "stops": [
              {
                "stopId": "7201",
                "placeId": "5001",
                "order": 1,
                "name": "해운대 카페",
                "address": "부산광역시 해운대구 해운대로 123"
              }
            ],
            "legs": []
          }
        ]
      }
    ]
  }
}
```

실제 성공 응답의 `candidates`는 `SHORTEST`, `PREFERENCE`, `BALANCED`를 각각 하나씩 포함해 정확히 3개다. `legs` 항목은 `legId`, `fromStopId`, `toStopId`, `order`, `distanceMeters`를 반환한다. 이동수단과 예상 소요 시간은 제공하지 않는다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `SCHEDULE_CANDIDATES_RETRIEVED` | 최신 후보 3개 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 작업 대상 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `SCHEDULE_GENERATION_JOB_NOT_FOUND` | 작업이 존재하지 않음 |
| `409 Conflict` | `SCHEDULE_GENERATION_NOT_COMPLETED` | 작업이 아직 성공하지 않았거나 실패함 |
| `410 Gone` | `SCHEDULE_CANDIDATES_SUPERSEDED` | 새 생성 성공으로 해당 작업의 미확정 후보가 삭제됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 일반 사용자 생성 작업 중 가장 최근에 성공한 작업의 후보만 조회·확정할 수 있다.
- FastAPI 결과가 전략별 한 개, 총 3개와 일정당 장소 3~20개 조건을 충족할 때만 한 번에 저장하고 작업을 `SUCCEEDED`로 변경한다.
- 후보 저장에 실패하면 모두 롤백하고 작업을 `FAILED`로 처리한다.

## 9. 일정 확정

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/trips/{tripId}/schedule` |
| 설명 | 최신 후보 중 하나를 여행방의 현재 확정 일정으로 선택한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 현재 방장 |
| 멱등성 | 같은 후보를 반복 확정해도 결과가 같은 자연 멱등 PUT |

### 요청

```json
{
  "scheduleId": "7001"
}
```

### 성공 응답

```json
{
  "code": "SCHEDULE_CONFIRMED",
  "message": "일정을 확정했습니다.",
  "data": {
    "tripId": "1001",
    "scheduleId": "7001",
    "strategy": "SHORTEST",
    "status": "ACTIVE",
    "confirmedAt": "2026-09-07T18:10:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `SCHEDULE_CONFIRMED` | 신규 확정 또는 같은 후보 재확인 성공 |
| `400 Bad Request` | `INVALID_REQUEST` | `scheduleId` 형식이 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_HOST_REQUIRED` | 현재 방장이 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `404 Not Found` | `SCHEDULE_CANDIDATE_NOT_FOUND` | 후보가 없거나 해당 여행방 후보가 아님 |
| `410 Gone` | `SCHEDULE_CANDIDATES_SUPERSEDED` | 최신 성공 작업의 후보가 아님 |
| `409 Conflict` | `SCHEDULE_CHANGE_NOT_ALLOWED` | 여행 시작일이 되어 확정할 수 없음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 가장 최근에 성공한 일반 생성 작업의 후보 3개 중 하나만 확정할 수 있다.
- 서울 시각 기준 여행 시작 전날 `23:59:59.999999`까지만 확정·재확정할 수 있다.
- 처음 확정하면 선택한 후보를 `ACTIVE`로 변경한다.
- 다른 후보를 재확정하면 기존 `ACTIVE` 일정을 `SUPERSEDED`, 새 일정을 `ACTIVE`로 변경한다.
- 같은 `scheduleId`가 이미 `ACTIVE`이면 상태와 `confirmedAt`을 바꾸지 않고 현재 결과를 반환한다.
- 상태 변경은 하나의 트랜잭션으로 처리하며 동시에 두 개의 `ACTIVE` 일정이 생기지 않게 한다. DB 잠금 방식은 API 계약에 포함하지 않는다.

## 10. 확정 일정 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}/schedule` |
| 설명 | 여행방의 현재 확정 일정과 날짜별 방문 순서·거리 정보를 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 활성 멤버 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "ACTIVE_SCHEDULE_RETRIEVED",
  "message": "확정 일정을 조회했습니다.",
  "data": {
    "tripId": "1001",
    "scheduleId": "7001",
    "strategy": "SHORTEST",
    "status": "ACTIVE",
    "editable": true,
    "totalDistanceMeters": 12100,
    "confirmedAt": "2026-09-07T18:10:00.123456+09:00",
    "days": [
      {
        "dayId": "7101",
        "dayNumber": 1,
        "date": "2026-09-12",
        "totalDistanceMeters": 12100,
        "stops": [
          {
            "stopId": "7201",
            "placeId": "5001",
            "order": 1,
            "source": "AI",
            "name": "경주역",
            "categoryName": "교통 > 기차역",
            "address": "경상북도 경주시",
            "roadAddress": "경상북도 경주시 태종로 685",
            "longitude": 129.2175,
            "latitude": 35.8443,
            "selectionReason": "여행 시작 지점과 가깝고 다음 장소로 이동하기 편리해요."
          },
          {
            "stopId": "7202",
            "placeId": "5002",
            "order": 2,
            "source": "MANUAL",
            "name": "황리단길",
            "categoryName": "관광명소",
            "address": "경상북도 경주시 황남동",
            "roadAddress": null,
            "longitude": 129.2107,
            "latitude": 35.8384,
            "selectionReason": null
          }
        ],
        "legs": [
          {
            "legId": "7301",
            "fromStopId": "7201",
            "toStopId": "7202",
            "order": 1,
            "distanceMeters": 950
          }
        ]
      }
    ]
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `ACTIVE_SCHEDULE_RETRIEVED` | 현재 확정 일정 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 해당 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `404 Not Found` | `ACTIVE_SCHEDULE_NOT_FOUND` | 아직 확정된 일정이 없음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 해당 여행방의 `ACTIVE` 일정 하나만 반환하며 과거 `SUPERSEDED` 일정과 미확정 후보는 포함하지 않는다.
- Day는 `dayNumber`, 활성 장소는 `order`, 이동 구간은 `order` 오름차순으로 반환한다.
- `REMOVED` 상태의 장소는 `stops`와 지도 표시 대상에서 제외한다.
- `source=AI`이면 FastAPI가 제공해 저장한 `selectionReason`을 반환한다. `source=MANUAL`이면 `selectionReason`은 `null`이다.
- 거리 합계는 현재 활성 `schedule_legs.distance_meters`의 합계다. 이동수단, 예상 소요 시간과 도로 경로 polyline은 제공하지 않는다.
- 클라이언트는 각 장소의 좌표를 방문 순서대로 직선 연결해 지도의 동선을 표시한다.
- `editable`은 요청 사용자가 현재 방장이고 서울 시각 기준 여행 시작 전날 `23:59:59.999999` 이전일 때만 `true`다. 실제 변경 API에서도 같은 권한과 시점을 다시 검증한다.

## 11. 확정 일정 장소 추가

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/trips/{tripId}/schedule/stops` |
| 설명 | 확정 일정의 지정한 Day와 순서에 장소를 삽입하고 영향받은 이동 구간만 갱신한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 현재 방장 |
| 멱등성 | `Idempotency-Key` 필수 |

### 요청

```json
{
  "scheduleDayId": "7101",
  "placeId": "5009",
  "position": 3
}
```

- `position`은 Day 안에서 1부터 시작하는 삽입 순서다.
- `1`은 가장 처음, 현재 장소 수에 `1`을 더한 값은 가장 마지막이며 그 사잇값은 두 장소 사이를 의미한다.

### 성공 응답

```json
{
  "code": "SCHEDULE_STOP_ADDED",
  "message": "일정에 장소를 추가했습니다.",
  "data": {
    "scheduleId": "7001",
    "day": {
      "dayId": "7101",
      "dayNumber": 1,
      "date": "2026-09-12",
      "stops": [
        {
          "stopId": "7209",
          "placeId": "5009",
          "order": 3,
          "name": "경주 카페",
          "address": "경상북도 경주시"
        }
      ],
      "legs": [
        {
          "legId": "7309",
          "fromStopId": "7202",
          "toStopId": "7209",
          "order": 2,
          "distanceMeters": 1800
        }
      ]
    }
  }
}
```

실제 응답의 `stops`와 `legs`에는 변경 후 해당 Day의 전체 장소와 이동 구간을 순서대로 반환한다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `201 Created` | `SCHEDULE_STOP_ADDED` | 장소와 영향받은 이동 구간 갱신 성공 |
| `400 Bad Request` | `INVALID_REQUEST` | ID 또는 `position` 형식이 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_HOST_REQUIRED` | 현재 방장이 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `404 Not Found` | `ACTIVE_SCHEDULE_NOT_FOUND` | 확정 일정이 없음 |
| `404 Not Found` | `SCHEDULE_DAY_NOT_FOUND` | Day가 현재 확정 일정에 속하지 않음 |
| `404 Not Found` | `PLACE_NOT_FOUND` | 저장된 장소가 없음 |
| `409 Conflict` | `SCHEDULE_CHANGE_NOT_ALLOWED` | 서울 날짜 기준 여행이 이미 시작됨 |
| `409 Conflict` | `SCHEDULE_MAXIMUM_STOPS_EXCEEDED` | 추가하면 일정 전체 장소가 20개를 초과함 |
| `409 Conflict` | `SCHEDULE_PLACE_ALREADY_EXISTS` | 같은 일정에 동일 장소가 이미 포함됨 |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | 같은 Key를 다른 요청 내용에 사용함 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `position` 이후의 활성 장소 `stop_order`를 한 칸씩 뒤로 이동해 새 장소를 삽입한다.
- 처음 또는 마지막에 추가하면 새로 생긴 인접 구간 하나만 계산한다.
- 두 장소 `A`, `B` 사이에 추가하면 기존 `A → B` 구간을 `A → 신규 장소`, `신규 장소 → B`로 교체한다.
- 백엔드는 각 구간의 두 장소 좌표로 거리를 계산한다. 기존 방문 순서와 다른 장소는 변경하지 않는다.
- 수동 추가에는 AI 생성용 음식점·카페 배치 제약을 적용하지 않는다.
- 거리 계산이나 저장이 실패하면 장소, 순서와 이동 구간 변경을 적용하지 않고 기존 일정을 유지한다.

## 12. 확정 일정 장소 삭제

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `DELETE` |
| URL | `/api/trips/{tripId}/schedule/stops/{stopId}` |
| 설명 | 확정 일정에서 장소를 제거하고 영향받은 이동 구간만 갱신한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 현재 방장 |
| 멱등성 | 삭제 결과가 동일한 자연 멱등 요청 |

### 성공 응답

```json
{
  "code": "SCHEDULE_STOP_DELETED",
  "message": "일정에서 장소를 삭제했습니다.",
  "data": {
    "scheduleId": "7001",
    "deletedStopId": "7203",
    "day": {
      "dayId": "7101",
      "dayNumber": 1,
      "date": "2026-09-12",
      "stops": [],
      "legs": []
    }
  }
}
```

실제 응답의 `stops`와 `legs`에는 변경 후 해당 Day의 전체 장소와 이동 구간을 순서대로 반환한다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `SCHEDULE_STOP_DELETED` | 장소와 영향받은 이동 구간 갱신 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_HOST_REQUIRED` | 현재 방장이 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `404 Not Found` | `SCHEDULE_STOP_NOT_FOUND` | 장소가 없거나 현재 확정 일정에 속하지 않음 |
| `409 Conflict` | `SCHEDULE_CHANGE_NOT_ALLOWED` | 서울 날짜 기준 여행이 이미 시작됨 |
| `409 Conflict` | `SCHEDULE_MINIMUM_STOPS_REQUIRED` | 삭제하면 일정 전체 장소가 3개 미만이 됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 처음 또는 마지막 장소를 삭제하면 해당 장소와 연결된 이동 구간만 제거한다.
- 두 장소 `A`, `B` 사이의 장소를 삭제하면 삭제 장소와 연결된 두 구간을 제거하고 두 장소 좌표로 `A → B` 거리를 계산해 구간 하나를 저장한다.
- 삭제한 장소 뒤의 활성 장소 `stop_order`와 이동 구간 순서를 연속된 값으로 당긴다.
- 장소는 `REMOVED`, `removed_at`으로 소프트 삭제하며 조회 응답에서는 제외한다.
- 수동 삭제에는 AI 생성용 음식점·카페 배치 제약을 적용하지 않는다.
- 거리 계산이나 저장이 실패하면 삭제, 순서와 이동 구간 변경을 적용하지 않고 기존 일정을 유지한다.

## 13. 최근 날씨 점검 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}/weather-checks/latest` |
| 설명 | 여행 D-1 날씨 점검 결과와 방장의 일정 결정·대체 작업 상태를 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 활성 멤버 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "LATEST_WEATHER_CHECK_RETRIEVED",
  "message": "최근 날씨 점검 결과를 조회했습니다.",
  "data": {
    "weatherCheckId": "8001",
    "tripId": "1001",
    "status": "SUCCEEDED",
    "scheduledCheckAt": "2026-09-11T10:00:00+09:00",
    "checkedAt": "2026-09-11T10:00:03.123456+09:00",
    "weatherCondition": "HEAVY_RAIN",
    "requiresDecision": true,
    "canDecide": true,
    "canRetry": false,
    "decision": null
  }
}
```

`decision`이 존재하면 다음 형태로 반환한다.

```json
{
  "decision": "REGENERATE",
  "decidedAt": "2026-09-11T10:05:00.123456+09:00",
  "replacementGenerationJobId": "4010",
  "replacementGenerationStatus": "RUNNING"
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `LATEST_WEATHER_CHECK_RETRIEVED` | 최근 점검과 결정 상태 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 해당 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `404 Not Found` | `WEATHER_CHECK_NOT_FOUND` | 아직 생성된 날씨 점검이 없음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 여행별 가장 최근 `weather_check` 하나를 반환한다. 상태는 `SCHEDULED`, `SUCCEEDED`, `FAILED` 중 하나다.
- `weatherCondition=NORMAL`이면 `requiresDecision=false`이며 일정 결정 API를 호출하지 않는다.
- 특이 기상이고 아직 최종 결정되지 않았으면 현재 방장에게만 `canDecide=true`를 반환한다.
- `REGENERATE` 작업이 최종 `FAILED`이고 아직 여행이 시작되지 않았으면 현재 방장에게 `canRetry=true`를 반환한다.
- 일반 멤버도 결정과 대체 작업 상태는 조회할 수 있지만 `canDecide`와 `canRetry`는 항상 `false`다.
- 날씨 제공자 오류 원문과 내부 `error_code`는 응답에 노출하지 않는다.

## 14. 날씨 기반 일정 결정

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/weather-checks/{weatherCheckId}/decision` |
| 설명 | 기존 일정 유지 또는 날씨 기반 대체 일정 생성을 결정한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 현재 방장 |
| 멱등성 | `KEEP`는 자연 멱등, `REGENERATE`는 `Idempotency-Key` 필수 |

### 요청

```json
{
  "decision": "REGENERATE"
}
```

`decision`은 `KEEP`, `REGENERATE` 중 하나다.

### 기존 일정 유지 성공 응답

```json
{
  "code": "WEATHER_SCHEDULE_DECISION_RECORDED",
  "message": "기존 일정을 유지합니다.",
  "data": {
    "weatherCheckId": "8001",
    "decision": "KEEP",
    "decidedAt": "2026-09-11T10:05:00.123456+09:00",
    "replacementGenerationJobId": null
  }
}
```

### 대체 일정 생성 접수 응답

```http
HTTP/1.1 202 Accepted
Location: /api/schedule-generation-jobs/4010
```

```json
{
  "code": "WEATHER_REPLAN_ACCEPTED",
  "message": "날씨를 반영한 대체 일정 생성을 시작했습니다.",
  "data": {
    "weatherCheckId": "8001",
    "decision": "REGENERATE",
    "replacementGenerationJobId": "4010",
    "replacementGenerationStatus": "QUEUED",
    "decidedAt": "2026-09-11T10:05:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `WEATHER_SCHEDULE_DECISION_RECORDED` | `KEEP` 결정 또는 이미 기록된 동일한 `KEEP` 조회 |
| `202 Accepted` | `WEATHER_REPLAN_ACCEPTED` | 최초 대체 작업 또는 실패 후 재시도 접수 |
| `400 Bad Request` | `INVALID_REQUEST` | 결정 값이나 필수 Header가 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_HOST_REQUIRED` | 현재 방장이 아님 |
| `404 Not Found` | `WEATHER_CHECK_NOT_FOUND` | 날씨 점검이 존재하지 않음 |
| `404 Not Found` | `ACTIVE_SCHEDULE_NOT_FOUND` | 대체할 현재 확정 일정이 없음 |
| `409 Conflict` | `WEATHER_CHECK_NOT_COMPLETED` | 날씨 점검이 아직 끝나지 않았거나 실패함 |
| `409 Conflict` | `WEATHER_CHANGE_NOT_REQUIRED` | 날씨가 `NORMAL`이라 결정이 필요하지 않음 |
| `409 Conflict` | `WEATHER_DECISION_ALREADY_FINALIZED` | 기존 결정과 다른 값이거나 대체 일정이 이미 성공함 |
| `409 Conflict` | `WEATHER_REPLAN_IN_PROGRESS` | 대체 일정 작업이 이미 실행 중임 |
| `409 Conflict` | `WEATHER_DECISION_CLOSED` | 서울 날짜 기준 여행이 이미 시작됨 |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | 같은 Key를 다른 요청 내용에 사용함 |
| `409 Conflict` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 Key의 요청이 아직 처리 중임 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `SUCCEEDED` 날씨 점검에서 `weatherCondition`이 `NORMAL`이 아닐 때만 결정할 수 있다.
- `KEEP`를 선택하면 최종 결정으로 기록하며 이후 `REGENERATE`로 변경할 수 없다. 동일한 `KEEP` 요청은 저장 시각을 바꾸지 않고 기존 결과를 반환한다.
- `REGENERATE`를 선택하면 기존 확정 일정, 여행 지역·기간, 날씨 조건과 그룹 취향을 고정해 `job_type=WEATHER_REPLAN`, `generation_sequence=NULL`인 작업을 생성한다.
- 날씨 대체 작업은 일반 사용자 재생성 3회와 별개이며, 동시에 하나만 실행할 수 있다.
- 대체 작업이 `FAILED`이면 사용자 횟수를 차감하지 않는다. 방장은 여행 시작 전까지 새 `Idempotency-Key`로 같은 `REGENERATE` 결정을 다시 요청할 수 있고, 결정의 `replacement_generation_job_id`는 가장 최근 작업으로 갱신한다.
- 대체 작업이 성공하면 하나의 `WEATHER_ALTERNATIVE` 일정을 만들고 별도 확인 없이 `ACTIVE`로 전환한다. 기존 `ACTIVE` 일정은 `SUPERSEDED`로 변경하며 이후 추가 날씨 재생성을 허용하지 않는다.
- 여행 시작 시각 이후에는 새로운 결정과 재시도를 거부한다. 시작 전에 접수된 작업은 취소하지 않되 결과 적용 트랜잭션 직전에 여행 시작 여부를 다시 검사한다.
- 검사 시점에 여행이 시작됐다면 새 일정과 하위 데이터를 저장하거나 활성화하지 않고 작업을 `FAILED`, 내부 `error_code=WEATHER_REPLAN_EXPIRED`로 종료한다. 이 내부 오류 코드는 사용자 응답에 노출하지 않으며 기존 `ACTIVE` 일정을 유지한다.
- 아무 결정도 하지 않은 채 여행이 시작되면 시스템이 `decided_by_member_id=NULL`인 `KEEP` 결정을 기록한다.

## 15. 지역 장소 검색

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/regions/{regionId}/places` |
| 설명 | 선택한 여행 지역의 광역 지역명을 검색어에 결합해 Google Places 장소를 검색한다. |
| 인증 | Access Token 필수 |
| 인가 | 로그인 사용자 |
| 페이지네이션 | cursor, 최초·추가 요청 모두 10개 고정 |

### 요청

```http
GET /api/regions/123/places?keyword=%EC%B9%B4%ED%8E%98&cursor={opaqueCursor}
Authorization: Bearer {accessToken}
```

| 필드 | 위치 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `regionId` | Path | string | Y | BIGINT 10진수 문자열 | 검색 문맥으로 사용할 여행 지역 ID |
| `keyword` | Query | string | Y | 앞뒤 공백 제거 후 1~100자 | 사용자가 입력한 원본 검색어 |
| `cursor` | Query | string | N | 최초 요청 시 생략 | 다음 Google Places 검색 페이지를 가리키는 불투명 cursor |

### 성공 응답

```json
{
  "code": "PLACES_SEARCHED",
  "message": "장소 검색 결과를 조회했습니다.",
  "data": {
    "items": [
      {
        "googlePlaceId": "ChIJ_PLANIT_EXAMPLE",
        "name": "해운대 카페",
        "categoryName": "음식점 > 카페",
        "address": "부산광역시 해운대구 우동 123",
        "roadAddress": "부산광역시 해운대구 해운대로 123",
        "longitude": 129.1585,
        "latitude": 35.1587,
        "phone": "051-000-0000",
        "placeUrl": "https://www.google.com/maps/place/?q=place_id:ChIJ_PLANIT_EXAMPLE"
      }
    ],
    "page": {
      "nextCursor": "opaque-next-cursor",
      "hasNext": true
    }
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `PLACES_SEARCHED` | 검색 성공, 결과가 없어도 빈 목록 반환 |
| `400 Bad Request` | `INVALID_REQUEST` | 검색어 형식이나 길이가 유효하지 않음 |
| `400 Bad Request` | `INVALID_CURSOR` | cursor가 유효하지 않거나 현재 검색 조건과 맞지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `REGION_NOT_FOUND` | 서비스 여행 지역이 존재하지 않음 |
| `503 Service Unavailable` | `PLACE_SEARCH_UNAVAILABLE` | Google Places 장소 검색을 일시적으로 사용할 수 없음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 서버는 `regionId`로 `regions.broad_region_name`을 조회해 사용자 검색어 앞에 공백으로 결합한 값을 Google Places API에 전달한다.
- 결합한 광역 지역명은 검색창과 응답에 노출하지 않는다. 좌표나 행정구역 코드로 결과를 사후 제외하지 않으므로 특정 지역 포함을 보장하는 하드 필터가 아니다.
- cursor에는 다음 Google Places 검색 페이지와 `regionId`, 검색어 문맥을 서버만 해석할 수 있는 형태로 담는다. 검색 조건이 달라지면 기존 cursor를 사용할 수 없다.
- 검색 결과는 이 API에서 `places`에 저장하지 않는다. 사용자가 결과를 선택했을 때 선택 장소 저장 API를 호출한다.
- 빈 결과는 `200 OK`, `items=[]`, `nextCursor=null`, `hasNext=false`로 반환한다.

## 16. 장소 상세 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/places/{placeId}` |
| 설명 | 서비스 DB에 저장된 장소의 표시 정보를 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 로그인 사용자 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "PLACE_RETRIEVED",
  "message": "장소 상세 정보를 조회했습니다.",
  "data": {
    "placeId": "5001",
    "regionId": "123",
    "googlePlaceId": "ChIJ_PLANIT_EXAMPLE",
    "name": "해운대 카페",
    "categoryName": "음식점 > 카페",
    "address": "부산광역시 해운대구 우동 123",
    "roadAddress": "부산광역시 해운대구 해운대로 123",
    "longitude": 129.1585,
    "latitude": 35.1587,
    "phone": "051-000-0000",
    "placeUrl": "https://www.google.com/maps/place/?q=place_id:ChIJ_PLANIT_EXAMPLE"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `PLACE_RETRIEVED` | 저장된 장소 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `PLACE_NOT_FOUND` | 저장된 장소가 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 이 API는 Google Places API를 다시 호출하지 않고 현재 `places` 행의 값을 반환한다.
- 장소가 설문 선호 장소인지 실제 일정 장소인지는 이 응답에서 구분하지 않는다. 해당 역할은 `survey_place_preferences`, `schedule_stops` 연결 관계가 결정한다.
- nullable 값은 빈 문자열 대신 `null`로 반환하고 BIGINT ID는 JSON 10진수 문자열로 반환한다.

## 17. 설문 현황·그룹 취향 집계 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}/survey-summary` |
| 설명 | 활성 멤버의 설문 제출 현황과 제출 완료 설문의 그룹 취향 평균을 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 활성 멤버 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "SURVEY_SUMMARY_RETRIEVED",
  "message": "설문 현황과 그룹 취향을 조회했습니다.",
  "data": {
    "tripId": "1001",
    "deadlineAt": "2026-09-11T23:59:59.999999+09:00",
    "activeMemberCount": 4,
    "submittedCount": 3,
    "progressPercent": 75,
    "allSubmitted": false,
    "categoryAverages": [
      {
        "categoryCode": "FOOD",
        "averageScore": 4.3
      },
      {
        "categoryCode": "ACTIVITY",
        "averageScore": 3.7
      }
    ]
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `SURVEY_SUMMARY_RETRIEVED` | 현황과 집계 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 해당 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 제출 현황의 분모와 분자는 현재 활성 멤버만 대상으로 한다. 나간 멤버와 탈퇴한 사용자는 제외한다.
- `progressPercent`는 `submittedCount / activeMemberCount × 100`을 정수로 반올림한 제출 진행률이며 취향 점수 환산값이 아니다.
- `categoryAverages`는 제출 완료된 활성 멤버의 최신 `survey_answers.score`만 카테고리별로 산술 평균하고 소수 첫째 자리로 반올림한다.
- 평균값은 `1.0~5.0` 범위이며 0~100 점수로 정규화하지 않는다.
- 제출 완료 설문이 없으면 `submittedCount=0`, `progressPercent=0`, `categoryAverages=[]`를 반환한다.
- 개인별 점수, 제외 카테고리와 꼭 가고 싶은 장소는 그룹 집계 응답에 노출하지 않는다.
- 문항·카테고리 표시 순서는 보류 중이므로 `categoryAverages` 배열의 표시 순서 계약도 아직 확정하지 않는다.
