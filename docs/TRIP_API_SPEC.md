# PlanIt 여행 API 명세

## 1. 문서 상태

- 공통 작성 규약은 [`API_SPEC_GUIDELINES.md`](./API_SPEC_GUIDELINES.md)를 따른다.
- 이 문서에서 `APPROVED`로 표시한 계약만 확정된 계약이다.
- 아직 상세화하지 않은 여행 API는 [`API_INVENTORY.md`](./API_INVENTORY.md)에서 `DRAFT`로 관리한다.

## 2. 공통 여행 지역 규칙

- 서비스에서 지원하는 광역·하위 여행 지역은 운영 전에 `regions` 테이블에 기준정보로 미리 등록한다.
- `regions`의 한 행은 광역·하위 지역 정보를 함께 가지며, API의 `regionId`는 해당 행의 PK인 `regions.id`다.
- `broad_region_code`, `broad_region_name`, `sub_region_code`, `sub_region_name`은 모두 필수 기준정보이며 기본값을 사용하지 않는다.
- `regionId`는 광역 지역 코드나 하위 지역 코드 자체가 아니다.
- 여행방 생성 요청에서는 최종 선택한 `regionId` 하나만 전달한다.
- `regionId` 누락은 `400 INVALID_REQUEST`, 존재하지 않는 값은 `404 REGION_NOT_FOUND`로 처리한다.

### 여행방 생성 날짜 규칙

- 여행 시작일은 서울 날짜 기준 최소 내일이다.
- 종료일은 시작일 이상이어야 하며, 시작일과 종료일을 포함해 최대 10일까지 허용한다.
- 설문 마감 날짜는 서울 날짜 기준 오늘부터 여행 시작일 전날까지 선택할 수 있다.
- 설문 마감 시각은 선택한 날짜의 `23:59:59.999999`로 저장하며 클라이언트가 별도 시간을 전달하지 않는다.
- 기본 설문 마감 날짜는 여행 전날이다.
- `1일 뒤`나 `3일 뒤`가 여행 시작일 이상이면 해당 선택지를 제공하지 않는다.

## 3. 서비스 지역 목록 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/regions` |
| 설명 | 여행방 생성 화면에서 선택할 사전 등록 여행 지역을 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 로그인 사용자 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |
| 페이지네이션 | 사용하지 않고 전체 목록 즉시 로드 |

### 요청

Path, Query와 request body는 사용하지 않는다.

```http
GET /api/regions
Authorization: Bearer {accessToken}
```

### 성공 응답

```json
{
  "code": "REGIONS_RETRIEVED",
  "message": "지역 목록을 조회했습니다.",
  "data": {
    "regions": [
      {
        "broadRegionCode": "11",
        "broadRegionName": "서울특별시",
        "subRegions": [
          {
            "regionId": "1",
            "subRegionCode": "11680",
            "subRegionName": "강남구"
          }
        ]
      }
    ]
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `REGIONS_RETRIEVED` | 하나 이상의 여행 지역 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `503 Service Unavailable` | `REGION_CATALOG_UNAVAILABLE` | 필수 지역 기준정보가 비어 있어 여행 지역을 제공할 수 없음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 서버는 `regions` 행을 `broadRegionCode`와 `broadRegionName` 기준으로 묶고 각 그룹의 하위 지역을 `subRegions`로 반환한다.
- `regionId`는 BIGINT PK이므로 JSON에서 10진수 문자열로 반환한다.
- 지역 기준정보는 여행방 생성에 필수이므로 `regions: []`를 정상 응답으로 반환하지 않는다.
- 지역 기준정보를 조회할 수 없으면 클라이언트는 다음 단계로 진행시키지 않고 오류 화면과 재시도를 제공한다.

## 4. 여행방 생성

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/trips` |
| 설명 | 여행방과 생성자의 방장 멤버십을 생성한다. |
| 인증 | Access Token 필수 |
| 인가 | 로그인 사용자 |
| 멱등성 | `Idempotency-Key` 필수 |

### 요청

```http
POST /api/trips
Authorization: Bearer {accessToken}
Idempotency-Key: {uuidV7}
Content-Type: application/json
```

```json
{
  "name": "부산 맛집 여행",
  "regionId": "123",
  "startDate": "2026-09-12",
  "endDate": "2026-09-14",
  "capacity": 4,
  "surveyDeadlineDate": "2026-09-11"
}
```

| 위치 | 필드 | 타입 | 필수 | 제약·기본값 | 설명 |
| --- | --- | --- | --- | --- | --- |
| Header | `Idempotency-Key` | string(UUIDv7) | Y | 같은 사용자 동작의 재시도에는 같은 값 사용 | 중복 여행방 생성 방지 |
| Body | `name` | string | Y | 앞뒤 공백 제거 후 1~12자 | 여행방 이름 |
| Body | `regionId` | string | Y | `regions.id`의 10진수 문자열 | 선택한 여행 지역 |
| Body | `startDate` | string(date) | Y | 서울 날짜 기준 최소 내일 | 여행 시작일 |
| Body | `endDate` | string(date) | Y | 시작일 이상, 포함 최대 10일 | 여행 종료일 |
| Body | `capacity` | integer | N | 기본값 4, 2~8 | 방장을 포함한 정원 |
| Body | `surveyDeadlineDate` | string(date) | N | 기본값 여행 전날, 오늘부터 여행 전날까지 | 설문 마감 날짜 |

서버는 기본값을 적용하고 요청 DTO를 검증한 뒤 멱등성 요청 해시를 계산한다. `surveyDeadlineDate`는 서울 시각의 해당 날짜 `23:59:59.999999`로 변환한다.

### 성공 응답

```http
HTTP/1.1 201 Created
Location: /api/trips/1001
Content-Type: application/json
```

```json
{
  "code": "TRIP_CREATED",
  "message": "여행방을 생성했습니다.",
  "data": {
    "tripId": "1001",
    "name": "부산 맛집 여행",
    "region": {
      "regionId": "123",
      "broadRegionCode": "26",
      "broadRegionName": "부산광역시",
      "subRegionCode": "26350",
      "subRegionName": "해운대구"
    },
    "startDate": "2026-09-12",
    "endDate": "2026-09-14",
    "capacity": 4,
    "memberCount": 1,
    "myRole": "HOST",
    "surveyDeadlineAt": "2026-09-11T23:59:59.999999+09:00",
    "createdAt": "2026-09-07T14:30:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `201 Created` | `TRIP_CREATED` | 여행방과 방장 멤버십 생성 완료 |
| `400 Bad Request` | `INVALID_REQUEST` | 이름, 날짜, 정원 또는 설문 마감 검증 실패 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `REGION_NOT_FOUND` | `regionId`에 해당하는 여행 지역이 없음 |
| `409 Conflict` | `TRIP_DATE_CONFLICT` | 현재 사용자의 다른 활성 여행과 날짜가 겹침 |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | 같은 Key를 다른 요청 내용에 사용함 |
| `409 Conflict` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 Key의 요청이 아직 처리 중임 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 여행방과 생성자의 `HOST` 멤버십은 하나의 트랜잭션으로 생성하며, 어느 한쪽이라도 실패하면 모두 롤백한다.
- 생성자의 멤버십은 활성 상태이고 `memberCount`는 1로 시작한다.
- 소프트 삭제된 여행방과 종료된 멤버십은 날짜 중복 검사에서 제외한다.
- `tripId`는 JPA의 `BIGINT AUTO_INCREMENT`로 생성하고 응답에서는 10진수 문자열로 반환한다.
- 여행방 기본 정보는 생성 후 수정할 수 없다.

## 5. 내 여행 목록 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips` |
| 설명 | 현재 사용자가 참여 중인 여행방을 진행 중·예정·종료 순서로 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 본인의 여행 멤버십 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |
| 페이지네이션 | cursor, 최초 10개 및 이후 10개 고정 |

### 요청

```http
GET /api/trips?cursor={opaqueCursor}
Authorization: Bearer {accessToken}
```

| 위치 | 필드 | 타입 | 필수 | 제약·기본값 | 설명 |
| --- | --- | --- | --- | --- | --- |
| Query | `cursor` | string | N | 최초 조회 시 생략 | 다음 페이지를 위한 불투명 cursor |

클라이언트가 조회 개수를 지정하는 `size` 파라미터는 사용하지 않는다.

### 성공 응답

```json
{
  "code": "TRIPS_RETRIEVED",
  "message": "여행 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "tripId": "1001",
        "name": "부산 맛집 여행",
        "region": {
          "regionId": "123",
          "broadRegionName": "부산광역시",
          "subRegionName": "해운대구"
        },
        "startDate": "2026-09-12",
        "endDate": "2026-09-14",
        "status": "UPCOMING",
        "capacity": 4,
        "memberCount": 3,
        "myRole": "MEMBER"
      }
    ],
    "nextCursor": "opaque-next-cursor",
    "hasNext": true
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `TRIPS_RETRIEVED` | 목록 조회 성공, 결과가 없으면 `items: []` |
| `400 Bad Request` | `INVALID_CURSOR` | cursor 형식 또는 서명이 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `status`는 서울 날짜를 기준으로 `ONGOING`, `UPCOMING`, `COMPLETED` 중 하나로 계산한다.
- 진행 중 여행을 먼저, 예정 여행을 시작일이 가까운 순서로, 종료 여행을 종료일이 최근인 순서로 정렬한다.
- 같은 정렬값에서는 `tripId` 내림차순으로 순서를 고정한다.
- cursor는 상태 순위, 기준 날짜와 `tripId`를 서버만 해석할 수 있는 불투명 값으로 인코딩한다.
- `deleted_at`이 기록된 여행방과 현재 사용자의 `left_at`이 기록된 멤버십은 제외한다.
- 빈 목록은 정상 상태이므로 `200 OK`와 `items: []`, `nextCursor: null`, `hasNext: false`를 반환한다.

## 6. 초대 링크 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}/invitation` |
| 설명 | 여행방의 고정 공유용 초대 토큰을 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 현재 방장 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 요청

```http
GET /api/trips/1001/invitation
Authorization: Bearer {accessToken}
```

| 위치 | 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- | --- |
| Path | `tripId` | string | Y | BIGINT 10진수 문자열 | 여행방 ID |

### 성공 응답

```json
{
  "code": "TRIP_INVITATION_RETRIEVED",
  "message": "초대 토큰을 조회했습니다.",
  "data": {
    "invitationToken": "mVj7_wQ2Y7kP9qV4Tg6uJX1bL3sZ8nA0cD5eF2hR9xM"
  }
}
```

클라이언트는 응답 토큰을 현재 frontend origin의 `/invitations/{invitationToken}` 경로와 조합해 공유할 전체 URL을 만든다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `TRIP_INVITATION_RETRIEVED` | 고정 초대 토큰 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_HOST_REQUIRED` | 해당 여행방의 현재 방장이 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 여행방마다 만료 시각이 없는 고정 초대 토큰 하나만 사용한다.
- 토큰이 아직 없으면 256비트 `SecureRandom` 값을 Base64 URL-safe 무패딩 문자열로 인코딩해 생성한다. 결과 길이는 43자이며 `VARCHAR(64)`에 저장한다.
- 현재 방장만 초대 토큰을 조회·공유할 수 있다.
- 동일 여행방의 이후 조회에서는 저장된 `trip_invitations.token` 원문을 그대로 반환하며 조회만으로 재발급하지 않는다.
- DB에는 전체 초대 URL이 아닌 토큰만 저장한다. frontend 도메인이 달라져도 저장값을 변경하지 않는다.
- 소프트 삭제된 여행방의 토큰은 즉시 무효이며 조회할 수 없다.
- 토큰은 일반 여행방·목록 응답에 포함하지 않고 애플리케이션 로그에도 기록하지 않는다.
- 이 원문 저장 방식은 DB 읽기 권한이 탈취되면 유효한 초대 링크가 노출되는 위험을 수용한 현재 MVP 계약이다.
- 비로그인 사용자가 `/invitations/{invitationToken}`에 접근하면 초대 정보를 먼저 공개하지 않고 로그인 페이지로 이동한다.
- 로그인 시작 시 초대 경로 전체를 검증된 `returnTo`로 보존하고, 로그인 또는 최초 사용자 생성 완료 후 같은 초대 페이지로 복귀한다.
- 초대 정보 확인 API와 초대 참여 API는 모두 Access Token을 요구한다.
- 참여 화면의 `초대한 사용자`는 별도 query parameter나 서명 없이 현재 방장의 사용자 정보를 표시한다. 방장이 위임되면 기존 링크에서도 새 방장을 표시한다.

## 7. 초대 정보 확인

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/invitations/{invitationToken}` |
| 설명 | 로그인 완료 후 여행방 참여 화면에 필요한 초대 정보를 확인한다. |
| 인증 | Access Token 필수 |
| 인가 | 로그인 사용자 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 요청

```http
GET /api/invitations/mVj7_wQ2Y7kP9qV4Tg6uJX1bL3sZ8nA0cD5eF2hR9xM
Authorization: Bearer {accessToken}
```

`invitationToken`은 Base64 URL-safe 무패딩 43자 형식이다. 형식이 잘못됐거나 저장된 토큰과 일치하지 않으면 모두 `INVITATION_NOT_FOUND`로 처리한다.

### 성공 응답

```json
{
  "code": "INVITATION_RETRIEVED",
  "message": "초대 정보를 조회했습니다.",
  "data": {
    "trip": {
      "tripId": "1001",
      "name": "부산 맛집 여행",
      "region": {
        "regionId": "123",
        "broadRegionName": "부산광역시",
        "subRegionName": "해운대구"
      },
      "startDate": "2026-09-12",
      "endDate": "2026-09-14",
      "memberCount": 3,
      "capacity": 4
    },
    "inviter": {
      "publicId": "01991f6e-7300-7b21-a3cc-1436db3df95e",
      "userName": "플랜잇방장",
      "profileImageUrl": "https://example.invalid/profile-image"
    }
  }
}
```

`inviter`에는 별도의 링크 생성자를 저장하지 않고 조회 시점의 현재 방장 정보를 반환한다. 프로필 이미지가 없으면 서비스 기본 이미지의 조회 URL을 반환한다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `INVITATION_RETRIEVED` | 참여 가능한 초대 정보 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `INVITATION_NOT_FOUND` | 토큰 형식이 잘못됐거나 존재하지 않음 |
| `410 Gone` | `INVITATION_TRIP_DELETED` | 초대 대상 여행방이 소프트 삭제됨 |
| `409 Conflict` | `TRIP_SURVEY_CLOSED` | 설문 마감 또는 AI 생성 시작으로 신규 참여가 종료됨 |
| `409 Conflict` | `TRIP_CAPACITY_EXCEEDED` | 활성 인원이 정원에 도달함 |
| `409 Conflict` | `TRIP_ALREADY_STARTED` | 서울 날짜 기준 여행이 이미 시작됨 |
| `409 Conflict` | `TRIP_ALREADY_JOINED` | 요청 사용자가 이미 참여 중임 |
| `409 Conflict` | `TRIP_DATE_CONFLICT` | 요청 사용자의 다른 활성 여행과 날짜가 겹침 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 비로그인 사용자는 이 API를 호출하기 전에 로그인 화면으로 이동하며, 로그인 완료 후 원래 초대 페이지로 복귀한다.
- 초대 정보 확인과 초대 참여는 동일한 참여 가능 조건과 오류 코드를 사용한다.
- 성공 응답은 참여 화면에 필요한 정보만 제공하며 전체 멤버 목록, 설문 내용과 상세 일정은 포함하지 않는다.
- `TRIP_ALREADY_JOINED`를 받은 클라이언트는 해당 여행방 상세 화면으로 이동할 수 있다.

## 8. 초대 참여

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/invitations/{invitationToken}/join` |
| 설명 | 초대 조건을 다시 검증하고 현재 사용자의 여행 멤버십을 생성한다. |
| 인증 | Access Token 필수 |
| 인가 | 로그인 사용자 |
| 멱등성 | `Idempotency-Key` 필수 |

### 요청

```http
POST /api/invitations/mVj7_wQ2Y7kP9qV4Tg6uJX1bL3sZ8nA0cD5eF2hR9xM/join
Authorization: Bearer {accessToken}
Idempotency-Key: {uuidV7}
```

request body는 사용하지 않는다.

### 성공 응답

```http
HTTP/1.1 201 Created
Location: /api/trips/1001
Content-Type: application/json
```

```json
{
  "code": "TRIP_JOINED",
  "message": "여행방에 참여했습니다.",
  "data": {
    "tripId": "1001",
    "memberId": "3001",
    "role": "MEMBER",
    "joinedAt": "2026-09-07T16:20:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `201 Created` | `TRIP_JOINED` | 멤버십 생성 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `INVITATION_NOT_FOUND` | 토큰 형식이 잘못됐거나 존재하지 않음 |
| `410 Gone` | `INVITATION_TRIP_DELETED` | 초대 대상 여행방이 소프트 삭제됨 |
| `409 Conflict` | `TRIP_SURVEY_CLOSED` | 설문 마감 또는 AI 생성 시작으로 신규 참여가 종료됨 |
| `409 Conflict` | `TRIP_CAPACITY_EXCEEDED` | 활성 인원이 정원에 도달함 |
| `409 Conflict` | `TRIP_ALREADY_STARTED` | 서울 날짜 기준 여행이 이미 시작됨 |
| `409 Conflict` | `TRIP_ALREADY_JOINED` | 요청 사용자가 이미 참여 중임 |
| `409 Conflict` | `TRIP_DATE_CONFLICT` | 요청 사용자의 다른 활성 여행과 날짜가 겹침 |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | 같은 Key를 다른 요청 내용에 사용함 |
| `409 Conflict` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 Key의 요청이 아직 처리 중임 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 서버는 정보 확인 이후 상태가 바뀔 수 있으므로 참여 요청 시 모든 참여 조건을 다시 검증한다.
- 활성 인원이 정원보다 적고, 설문 참여가 열려 있고, 여행이 시작되지 않았으며, 기존 멤버십과 날짜 충돌이 없을 때만 `MEMBER` 멤버십을 생성한다.
- 참여 조건 검증과 멤버십 생성은 하나의 트랜잭션으로 처리하고 동시 요청에서도 정원을 초과하거나 중복 멤버십이 만들어지지 않게 한다. DB 잠금 방식은 API 계약에 포함하지 않는다.
- 멤버십 생성 후 해당 사용자를 포함해 설문 진행률을 다시 계산한다.
- `tripId`와 `memberId`는 BIGINT PK이므로 JSON에서 10진수 문자열로 반환한다.

## 9. 여행방 상세 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}` |
| 설명 | 여행방 정보, 활성 멤버, 설문 진행률과 일정 진행 참조값을 조회한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 활성 멤버 |
| 멱등성 | 조회 API이므로 별도 Key 불필요 |

### 성공 응답

```json
{
  "code": "TRIP_RETRIEVED",
  "message": "여행방을 조회했습니다.",
  "data": {
    "tripId": "1001",
    "name": "부산 맛집 여행",
    "region": {
      "regionId": "123",
      "broadRegionName": "부산광역시",
      "subRegionName": "해운대구"
    },
    "startDate": "2026-09-12",
    "endDate": "2026-09-14",
    "status": "UPCOMING",
    "capacity": 4,
    "memberCount": 3,
    "myRole": "MEMBER",
    "members": [
      {
        "memberId": "2001",
        "publicId": "01991f6e-7300-7b21-a3cc-1436db3df95e",
        "userName": "플랜잇방장",
        "profileImageUrl": "https://example.invalid/profile-image",
        "role": "HOST",
        "isMe": false,
        "joinedAt": "2026-09-07T14:30:00.123456+09:00"
      }
    ],
    "survey": {
      "deadlineAt": "2026-09-11T23:59:59.999999+09:00",
      "newMemberJoinClosed": true,
      "resubmissionAllowed": true,
      "submittedCount": 2,
      "totalMemberCount": 3,
      "progressPercent": 66,
      "mySubmittedAt": null
    },
    "schedule": {
      "activeScheduleId": null,
      "latestGenerationJobId": "4001",
      "latestGenerationStatus": "RUNNING"
    },
    "createdAt": "2026-09-07T14:30:00.123456+09:00"
  }
}
```

### 오류 응답

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 해당 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `members`에는 `left_at`이 없는 활성 멤버만 포함하며, 방장을 첫 번째로 두고 나머지는 `joined_at`, `memberId` 오름차순으로 정렬한다.
- `progressPercent`는 `제출한 활성 멤버 수 / 전체 활성 멤버 수 × 100`의 소수점을 버린 정수다.
- `newMemberJoinClosed`는 설문 마감 시각이 지났거나 일정 생성 작업이 시작됐으면 `true`다.
- AI 생성 시작은 기존 활성 멤버의 설문 재제출을 그 자체로 차단하지 않는다. 재제출 내용은 최신 집계에는 반영하지만 이미 실행 중인 AI 작업의 스냅샷과 결과에는 반영하지 않는다.
- `latestGenerationJobId`와 상태는 재접속 시 작업·후보 화면을 복구하기 위한 참조값이다. 작업이 한 번도 없으면 둘 다 `null`이다.
- `activeScheduleId`는 현재 확정된 `ACTIVE` 일정 ID이며 없으면 `null`이다. 상세 Day·장소·이동 구간은 확정 일정 조회 API가 담당한다.
- 사용자 프로필 이미지가 없으면 서비스 기본 이미지 조회 URL을 반환한다.

## 10. 여행방 나가기

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `DELETE` |
| URL | `/api/trips/{tripId}/members/me` |
| 설명 | 여행 시작 전에 현재 사용자의 멤버십을 종료한다. |
| 인증 | Access Token 필수 |
| 인가 | 해당 여행방의 활성 멤버 |
| 멱등성 | DELETE 의미상 멱등, 별도 Key 불필요 |

request body는 사용하지 않는다.

### 성공 응답

```json
{
  "code": "TRIP_MEMBERSHIP_ENDED",
  "message": "여행방에서 나갔습니다.",
  "data": {
    "tripDeleted": false,
    "newHostMemberId": "2002"
  }
}
```

`newHostMemberId`는 방장 위임이 없으면 `null`이다. `tripDeleted`가 `true`이면 `newHostMemberId`도 `null`이다.

### 오류 응답

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `TRIP_MEMBER_REQUIRED` | 해당 여행방의 활성 멤버가 아님 |
| `404 Not Found` | `TRIP_NOT_FOUND` | 여행방이 없거나 소프트 삭제됨 |
| `409 Conflict` | `TRIP_LEAVE_NOT_ALLOWED` | 서울 날짜 기준 여행이 시작됐거나 종료됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 서버는 서울 날짜가 `startDate`보다 이른 경우에만 나가기를 허용하고 현재 멤버십의 `left_at`을 기록한다.
- 방장이 나간 뒤 활성 멤버가 2명 이상 남으면 `joined_at`, `memberId`가 가장 빠른 멤버에게 방장 역할을 이전한다.
- 한 번이라도 활성 멤버가 2명 이상이었던 방이 나가기로 1명만 남으면 여행방을 소프트 삭제하고 남은 멤버십도 종료한다.
- 생성 후 계속 방장 한 명뿐이었던 방에서 마지막 방장이 나가면 소유자 없는 방을 남기지 않고 여행방을 소프트 삭제한다.
- 자동 삭제 시 후보·확정 일정과 하위 Day·장소·이동 구간을 애플리케이션 서비스 로직에서 함께 정리한다. 진행 중인 AI 작업은 취소하지 않지만 완료 결과를 반영하지 않으며 초대 토큰도 즉시 무효로 취급한다.
- 멤버십 종료, 방장 위임 또는 방 자동 삭제는 하나의 트랜잭션으로 처리한다. 구체적인 DB 잠금 방식은 API 계약에 포함하지 않는다.
