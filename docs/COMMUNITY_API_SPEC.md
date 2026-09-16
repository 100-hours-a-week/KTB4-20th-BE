# PlanIt 커뮤니티 API 명세

## 1. 공통 정책

- 이 문서의 사용자용 HTTP API 5개는 `APPROVED`다.
- 커뮤니티는 로그인 사용자만 접근할 수 있으며 모든 API에 Access Token이 필요하다.
- 여행 종료 시점의 `ACTIVE` 확정 일정 버전을 `community_posts.schedule_id`로 연결해 자동 공개한다.
- 별도 공개 동의, 비공개 전환과 게시물 삭제 API는 제공하지 않는다.
- 게시물과 공개된 일정 버전은 별도 삭제 정책 없이 계속 보관한다.
- 외부 공개 응답에는 여행 멤버의 이름, 설문 원본과 개인별 선정 이유를 포함하지 않는다.
- 조회수는 날짜가 바뀌어도 사용자·게시물 조합당 전체 기간 한 번만 증가한다.
- 좋아요는 사용자·게시물 조합당 하나만 유지하고 언제든 취소할 수 있다.
- 인기 점수는 `(likeCount × 3 + ln(viewCount)) / (elapsedHours + 2)^1.2`를 사용한다. `ln`은 자연로그이며 `viewCount`가 0 또는 1이면 로그 항목은 0으로 계산한다.
- 인기 점수는 DB에 고정값으로 저장하지 않는다. 최초 목록 요청의 `rankingAsOf`와 게시물 `created_at`으로 `elapsedHours`를 구해 조회 시 계산하므로, 반응이 추가되지 않은 게시물의 점수는 시간이 흐를수록 감소한다.

## 2. 커뮤니티 일정 목록

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/community/posts` |
| 인증 | Access Token 필수 |
| 페이지네이션 | cursor, 최초·추가 요청 모두 10개 고정 |

### 요청

```http
GET /api/community/posts?regionId=123&cursor={opaqueCursor}
Authorization: Bearer {accessToken}
```

| 필드 | 위치 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| `regionId` | Query | string | N | 생략하면 전체, 지정하면 해당 여행 지역만 조회 |
| `cursor` | Query | string | N | 최초 요청 시 생략하는 불투명 cursor |

### 성공 응답

```json
{
  "code": "COMMUNITY_POSTS_RETRIEVED",
  "message": "커뮤니티 일정을 조회했습니다.",
  "data": {
    "rankingAsOf": "2026-09-07T21:00:00.123456+09:00",
    "items": [
      {
        "postId": "11001",
        "tripName": "경주 역사 여행",
        "region": {
          "regionId": "123",
          "broadRegionName": "경상북도",
          "subRegionName": "경주시"
        },
        "startDate": "2026-08-26",
        "endDate": "2026-08-28",
        "dayCount": 3,
        "placeCount": 12,
        "likeCount": 42,
        "viewCount": 315,
        "liked": true,
        "createdAt": "2026-08-29T00:00:00.123456+09:00"
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
| `200 OK` | `COMMUNITY_POSTS_RETRIEVED` | 전체 또는 지역별 목록 조회 성공 |
| `400 Bad Request` | `INVALID_CURSOR` | cursor가 유효하지 않거나 현재 필터와 맞지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `REGION_NOT_FOUND` | 지정한 서비스 여행 지역이 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `regionId`를 생략하면 전체, 지정하면 `community_posts.region_id`가 같은 게시물만 조회한다.
- 목록은 `rankingAsOf` 시점의 인기 점수 내림차순, 점수가 같으면 `postId` 내림차순으로 정렬한다.
- `elapsedHours`는 `createdAt`부터 `rankingAsOf`까지 경과한 시간을 시간 단위 소수로 계산하며 음수가 되면 0으로 처리한다.
- 첫 페이지의 첫 항목을 전체 또는 선택 지역의 대표 인기 일정으로 사용하며 별도 대표 API를 제공하지 않는다.
- cursor는 최초 요청의 `rankingAsOf`, 지역 필터, 마지막 인기 점수와 `postId`를 서버만 해석할 수 있는 형태로 유지한다.
- 빈 목록은 `200 OK`, `items=[]`, `nextCursor=null`, `hasNext=false`로 반환한다.

## 3. 커뮤니티 일정 상세

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/community/posts/{postId}` |
| 인증 | Access Token 필수 |

### 성공 응답

```json
{
  "code": "COMMUNITY_POST_RETRIEVED",
  "message": "커뮤니티 일정 상세를 조회했습니다.",
  "data": {
    "postId": "11001",
    "tripName": "경주 역사 여행",
    "region": {
      "regionId": "123",
      "broadRegionName": "경상북도",
      "subRegionName": "경주시"
    },
    "startDate": "2026-08-26",
    "endDate": "2026-08-28",
    "likeCount": 42,
    "viewCount": 315,
    "liked": true,
    "schedule": {
      "scheduleId": "7001",
      "totalDistanceMeters": 12100,
      "days": [
        {
          "dayNumber": 1,
          "date": "2026-08-26",
          "totalDistanceMeters": 6400,
          "stops": [
            {
              "order": 1,
              "placeId": "5001",
              "name": "경주역",
              "categoryName": "교통 > 기차역",
              "address": "경상북도 경주시",
              "roadAddress": "경상북도 경주시 태종로 685",
              "longitude": 129.2175,
              "latitude": 35.8443
            }
          ],
          "legs": [
            {
              "order": 1,
              "fromPlaceId": "5001",
              "toPlaceId": "5002",
              "distanceMeters": 950
            }
          ]
        }
      ]
    },
    "createdAt": "2026-08-29T00:00:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `COMMUNITY_POST_RETRIEVED` | 공개 일정 상세 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `COMMUNITY_POST_NOT_FOUND` | 공개 게시물이 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 게시물이 연결한 확정 일정 버전을 조회하며 현재 여행방 일정이 이후 변경돼도 공개 버전을 바꾸지 않는다.
- 장소는 Day와 방문 순서대로 반환하고 지도는 좌표를 순서대로 직선 연결한다.
- 거리는 제공하지만 이동수단, 예상 소요 시간과 도로 경로 polyline은 제공하지 않는다.
- 멤버 이름, 개인 설문, `schedule_stops.selection_reason`은 응답하지 않는다.
- 상세 조회만으로 조회수를 증가시키지 않으며 화면 진입 시 조회 기록 API를 별도로 호출한다.

## 4. 게시물 조회 기록

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/community/posts/{postId}/views` |
| 인증 | Access Token 필수 |
| 멱등성 | 사용자·게시물별 전체 기간 한 번만 반영하는 자연 멱등 요청 |

request body와 `Idempotency-Key`는 사용하지 않는다.

### 성공 응답

```json
{
  "code": "COMMUNITY_VIEW_RECORDED",
  "message": "게시물 조회를 반영했습니다.",
  "data": {
    "postId": "11001",
    "counted": true,
    "viewCount": 316
  }
}
```

이미 조회한 사용자의 반복 요청은 `counted=false`와 현재 조회수를 반환한다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `COMMUNITY_VIEW_RECORDED` | 최초 조회 반영 또는 기존 조회 확인 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `COMMUNITY_POST_NOT_FOUND` | 공개 게시물이 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 현재 사용자와 게시물 조합의 `community_view_events`가 없을 때만 행을 생성하고 `view_count`를 1 증가시킨다.
- 날짜, 세션, 기기와 재접속 여부가 바뀌어도 같은 사용자의 조회수는 다시 증가하지 않는다.
- 조회 이벤트 생성과 게시물 조회수 증가는 하나의 원자적 처리로 수행한다. DB 동시성 제어 방식은 API 계약에 포함하지 않는다.

## 5. 좋아요 등록

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/community/posts/{postId}/like` |
| 인증 | Access Token 필수 |
| 멱등성 | 이미 좋아요 상태이면 현재 상태를 반환하는 자연 멱등 PUT |

request body는 사용하지 않는다.

### 성공 응답

```json
{
  "code": "COMMUNITY_POST_LIKED",
  "message": "좋아요를 등록했습니다.",
  "data": {
    "postId": "11001",
    "liked": true,
    "likeCount": 43
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `COMMUNITY_POST_LIKED` | 최초 등록 또는 기존 좋아요 상태 반환 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `COMMUNITY_POST_NOT_FOUND` | 공개 게시물이 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 현재 사용자와 게시물 조합의 좋아요가 없을 때만 `community_likes`를 생성하고 `like_count`를 1 증가시킨다.
- 좋아요 생성과 집계값 증가는 하나의 원자적 처리로 수행한다.

## 6. 좋아요 취소

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `DELETE` |
| URL | `/api/community/posts/{postId}/like` |
| 인증 | Access Token 필수 |
| 멱등성 | 이미 좋아요가 없는 상태에서도 현재 상태를 반환하는 자연 멱등 DELETE |

### 성공 응답

```json
{
  "code": "COMMUNITY_POST_UNLIKED",
  "message": "좋아요를 취소했습니다.",
  "data": {
    "postId": "11001",
    "liked": false,
    "likeCount": 42
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `COMMUNITY_POST_UNLIKED` | 좋아요 삭제 또는 기존 미등록 상태 반환 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `COMMUNITY_POST_NOT_FOUND` | 공개 게시물이 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 좋아요가 존재하면 삭제하고 `like_count`를 1 감소시킨다. 이미 없으면 집계값을 변경하지 않는다.
- 좋아요 삭제와 집계값 감소는 하나의 원자적 처리로 수행하며 `like_count`는 0 미만이 될 수 없다.

## 7. 여행 종료 자동 공개

- 서울 날짜 기준 여행 종료일 다음 날 `00:00:00`부터 공개 대상이 된다.
- 종료된 여행의 당시 `ACTIVE` 확정 일정 하나로 `community_posts`를 한 번만 생성한다.
- 자동 공개 작업이 재실행돼도 같은 여행의 게시물을 중복 생성하지 않는다.
- 공개 후에는 연결된 일정 버전을 커뮤니티용 불변 버전으로 보존한다.
- 자동 공개 실패는 여행 데이터에 영향을 주지 않으며 내부 작업에서 재시도한다.
