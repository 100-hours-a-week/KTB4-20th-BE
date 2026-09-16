# PlanIt 알림 API 명세

## 1. 공통 정책

- 이 문서의 사용자용 HTTP API 4개는 `APPROVED`다.
- 인앱 알림만 범위에 포함하며 푸시 알림은 제공하지 않는다.
- 현재 로그인 사용자에게 생성된 알림만 조회·변경할 수 있다.
- 알림은 생성 후 90일이 되면 `deleted_at`을 기록해 소프트 삭제하고 사용자 API에서 제외한다.
- 이동 대상이 만료돼도 90일 전까지 알림과 읽음 상태를 유지한다.
- 만료된 알림도 읽기 전까지 미읽음 수에 포함한다. 선택하면 읽음 처리하지만 대상 화면으로 이동하지 않는다.
- 상대 시간 문구는 API가 제공하지 않는다. 클라이언트가 `createdAt`을 서울 시각 기준으로 변환해 표시한다.
- 동일 여행·사용자·날짜·알림 유형의 중복 생성은 서버 내부 `dedupe_key`로 방지한다.

## 2. 알림 목록

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/notifications` |
| 인증·인가 | Access Token 본인 |
| 페이지네이션 | 최초 20개, 이후 cursor로 10개씩 |

```http
GET /api/notifications?cursor={opaqueCursor}
Authorization: Bearer {accessToken}
```

```json
{
  "code": "NOTIFICATIONS_RETRIEVED",
  "message": "알림 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "notificationId": "16001",
        "type": "TRIP_WEATHER_ALERT",
        "title": "여행지에 많은 비가 예상돼요",
        "body": "방장이 일정 변경 여부를 확인하고 있어요.",
        "target": {
          "type": "TRIP",
          "id": "1001",
          "status": "ACTIVE"
        },
        "read": false,
        "readAt": null,
        "createdAt": "2026-09-07T10:00:00.123456+09:00"
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
| `200 OK` | `NOTIFICATIONS_RETRIEVED` | 목록 조회 성공, 빈 목록 포함 |
| `400 Bad Request` | `INVALID_CURSOR` | cursor 형식이나 사용자 문맥이 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `deleted_at IS NULL`인 현재 사용자 알림만 `created_at`, `id` 내림차순으로 조회한다.
- 첫 요청은 cursor를 생략하고 20개를 받는다. 다음 요청부터는 직전 `nextCursor`를 사용하고 10개씩 받는다.
- 이동 대상이 만료되면 `target.status=EXPIRED`이며 `target.id`는 기존 값을 유지한다.
- 이동 대상이 없는 안내 알림은 `target=null`이다.
- 빈 목록은 `items=[]`, `nextCursor=null`, `hasNext=false`로 반환한다.

## 3. 미읽음 알림 수

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/notifications/unread-count` |
| 인증·인가 | Access Token 본인 |

```json
{
  "code": "UNREAD_NOTIFICATION_COUNT_RETRIEVED",
  "message": "읽지 않은 알림 수를 조회했습니다.",
  "data": {
    "unreadCount": 3,
    "hasUnread": true
  }
}
```

- `deleted_at IS NULL`, `read_at IS NULL`인 현재 사용자 알림을 계산한다.
- 이동 대상이 만료된 알림도 읽지 않았다면 포함한다.
- 인증 실패는 `401 AUTHENTICATION_REQUIRED`, 서버 오류는 `500 INTERNAL_SERVER_ERROR`다.

## 4. 개별 알림 읽음

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/notifications/{notificationId}/read` |
| 인증·인가 | Access Token 본인 |
| 멱등성 | 이미 읽은 알림은 기존 읽음 시각을 반환하는 자연 멱등 PUT |

request body는 사용하지 않는다.

```json
{
  "code": "NOTIFICATION_READ",
  "message": "알림을 읽음 처리했습니다.",
  "data": {
    "notificationId": "16001",
    "read": true,
    "readAt": "2026-09-07T11:00:00.123456+09:00",
    "targetStatus": "EXPIRED",
    "navigable": false
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `NOTIFICATION_READ` | 최초 읽음 또는 기존 읽음 상태 반환 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `NOTIFICATION_NOT_FOUND` | 본인 알림이 아니거나 이미 소프트 삭제됨 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 최초 요청에서만 현재 서울 시각으로 `read_at`을 기록한다. 반복 요청은 기존 시각을 변경하지 않는다.
- `targetStatus=EXPIRED`이면 `navigable=false`를 반환하며 클라이언트는 “더 이상 확인할 수 없는 항목입니다”를 표시한다.
- 활성 대상이면 `navigable=true`이고 목록 응답의 `target.type`, `target.id`를 사용해 이동한다.

## 5. 알림 모두 읽음

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/notifications/read-all` |
| 인증·인가 | Access Token 본인 |
| 멱등성 | 이미 모두 읽은 상태에서도 결과가 같은 자연 멱등 PUT |

request body는 사용하지 않는다.

```json
{
  "code": "ALL_NOTIFICATIONS_READ",
  "message": "모든 알림을 읽음 처리했습니다.",
  "data": {
    "updatedCount": 3,
    "readAt": "2026-09-07T11:05:00.123456+09:00",
    "unreadCount": 0
  }
}
```

- `deleted_at IS NULL`, `read_at IS NULL`인 현재 사용자 알림에 같은 읽음 시각을 기록한다.
- 이미 모두 읽었으면 `updatedCount=0`, `unreadCount=0`을 반환한다.
- 대상 만료 여부와 관계없이 모든 미읽음 알림을 처리한다.
- 인증 실패는 `401 AUTHENTICATION_REQUIRED`, 서버 오류는 `500 INTERNAL_SERVER_ERROR`다.

## 6. 알림 소프트 삭제

- 사용자용 삭제 API는 제공하지 않는다.
- 내부 정리 작업이 `created_at`으로부터 90일이 지난 알림의 `deleted_at`을 기록한다.
- 소프트 삭제 작업이 재실행돼도 이미 삭제된 행의 `deleted_at`을 변경하지 않는다.
- 소프트 삭제된 알림은 목록, 미읽음 수와 읽음 처리 대상에서 모두 제외한다.
- 물리 삭제 시점은 현재 계약에 포함하지 않는다.
