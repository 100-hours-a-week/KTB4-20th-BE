# PlanIt 지역 오픈 채팅 API 명세

## 1. 문서 상태와 책임 분리

- 이 문서의 HTTP API 7개는 `APPROVED`다.
- 실시간 메시지 송수신은 Spring WebSocket + STOMP로 구현하기로 결정했으며, 기술 선택은 `APPROVED`다. 연결 경로·인증 전달 방식·ACK 형식은 추후 결정하므로 `DRAFT`다.
- HTTP는 채팅방 목록, 정책 동의, 입퇴장, 과거 메시지와 이미지 업로드 준비를 담당한다.
- 실시간 인터페이스는 연결 이후의 새 텍스트·이미지 메시지와 차단·제재 통지를 담당한다.
- 실시간 연결이 끊긴 동안의 메시지는 HTTP 메시지 이력 API로 복구한다.
- 메시지 `clientMessageId`와 중복 방지는 제공하지 않는다. 전송 결과를 확인하지 못한 클라이언트가 같은 내용을 다시 전송하면 별도 메시지로 저장될 수 있다.

## 2. 공통 채팅 정책

- 지역 채팅은 여행방 내부 채팅이 아니라 서비스 사용자가 참여하는 지역별 공개 채팅이다.
- 메시지는 앞뒤 공백 제거 후 1~1000자이며 빈 텍스트는 허용하지 않는다.
- 동일 메시지 반복, 과도하게 빠른 전송과 욕설·모욕 등 정책 위반 메시지는 `BLOCKED`로 저장하되 실시간 전송과 일반 이력 조회에서 제외한다.
- 발신자에게만 차단 사유, 현재 위반 횟수와 제재 정보를 전달한다.
- 위반 3회마다 제재를 한 번 적용하고 다음 제재 판단용 위반 횟수는 다시 0부터 계산한다. 위반·제재 이력과 제재 단계는 유지한다.
- 제재 기간은 `1일 → 4일 → 7일 → 14일 → 30일 → 60일`이며 이후에도 60일이다.
- 제재는 사용자 전체에 적용해 모든 지역 채팅방 입장과 메시지 전송을 막는다.
- 일반 공개 메시지는 생성 후 90일 동안 보관한 뒤 삭제한다. 위반·제재 근거인 `BLOCKED` 메시지는 제재 이력과 함께 보존한다.
- 채팅 이미지는 JPEG, PNG, WebP만 허용하며 최대 크기는 5 MB다.

## 3. 지역 채팅방 목록

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/regional-chat-rooms` |
| 인증 | Access Token 필수 |
| 페이지네이션 | 전체 즉시 조회 |

### 성공 응답

```json
{
  "code": "REGIONAL_CHAT_ROOMS_RETRIEVED",
  "message": "지역 채팅방 목록을 조회했습니다.",
  "data": {
    "items": [
      {
        "roomId": "3001",
        "regionId": "123",
        "name": "부산광역시 해운대구",
        "participantCount": 128,
        "relatedToMyTrip": true,
        "joined": false,
        "canJoin": true
      }
    ]
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `REGIONAL_CHAT_ROOMS_RETRIEVED` | 전체 채팅방 목록 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 현재 사용자가 참여 중인 여행 지역의 채팅방을 먼저 배치한다.
- 나머지는 현재 활성 참여자 수 내림차순, 참여자 수가 같으면 지역명 가나다순으로 정렬한다.
- `canJoin`은 활성 제재가 없고 현재 정책에 동의했을 때 `true`다. 실제 입장 API에서도 다시 검증한다.

## 4. 현재 채팅 정책 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/chat-policy` |
| 인증 | Access Token 필수 |

### 성공 응답

```json
{
  "code": "CHAT_POLICY_RETRIEVED",
  "message": "현재 채팅 운영 정책을 조회했습니다.",
  "data": {
    "policyVersionId": "9001",
    "version": "2026-09-01",
    "title": "지역 채팅 운영 정책",
    "content": "지역 채팅 운영 정책 본문",
    "effectiveAt": "2026-09-01T00:00:00+09:00",
    "consented": false,
    "consentedAt": null
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `CHAT_POLICY_RETRIEVED` | 현재 `ACTIVE` 정책과 동의 상태 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `503 Service Unavailable` | `ACTIVE_CHAT_POLICY_UNAVAILABLE` | 활성 정책이 없어 채팅을 제공할 수 없음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- `content`는 HTML이나 Markdown이 아닌 평문이다. 클라이언트는 이를 HTML로 해석하거나 DOM에 직접 삽입하지 않고 일반 텍스트로 렌더링한다.
- 현재 `ACTIVE` 상태이면서 적용 시각이 지난 정책 하나를 반환한다.
- `consented`는 사용자가 반환된 정확한 `policyVersionId`에 동의했는지를 나타낸다.
- 새 정책 버전이 활성화되면 과거 버전 동의로 입장할 수 없으며 새 정책에 다시 동의해야 한다.

## 5. 채팅 정책 동의

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/chat-policy/consent` |
| 인증 | Access Token 필수 |
| 멱등성 | 동일 정책 버전 재동의 결과가 같은 자연 멱등 PUT |

### 요청

```json
{
  "policyVersionId": "9001"
}
```

### 성공 응답

```json
{
  "code": "CHAT_POLICY_CONSENT_RECORDED",
  "message": "채팅 운영 정책에 동의했습니다.",
  "data": {
    "policyVersionId": "9001",
    "consentedAt": "2026-09-07T20:00:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `CHAT_POLICY_CONSENT_RECORDED` | 최초 동의 또는 기존 동일 동의 반환 |
| `400 Bad Request` | `INVALID_REQUEST` | 정책 버전 ID 형식이 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `409 Conflict` | `CHAT_POLICY_VERSION_NOT_ACTIVE` | 요청한 정책이 현재 활성 버전이 아님 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

## 6. 채팅방 입장

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/regional-chat-rooms/{roomId}/members/me` |
| 인증 | Access Token 필수 |
| 멱등성 | 이미 참여 중이면 기존 참여 상태를 반환하는 자연 멱등 PUT |

### 성공 응답

```json
{
  "code": "REGIONAL_CHAT_ROOM_JOINED",
  "message": "지역 채팅방에 입장했습니다.",
  "data": {
    "roomId": "3001",
    "joinedAt": "2026-09-07T20:01:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `REGIONAL_CHAT_ROOM_JOINED` | 최초 입장 또는 기존 활성 참여 반환 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `REGIONAL_CHAT_ROOM_NOT_FOUND` | 채팅방이 존재하지 않음 |
| `409 Conflict` | `CHAT_POLICY_CONSENT_REQUIRED` | 현재 활성 정책에 동의하지 않음 |
| `423 Locked` | `CHAT_SANCTION_ACTIVE` | 사용자에게 적용 중인 채팅 제재가 있음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 과거에 퇴장한 참여 이력이 있으면 새 행을 중복 생성하지 않고 다시 활성화한다.
- 활성 제재가 끝난 사용자는 현재 정책에 동의한 상태라면 다시 입장할 수 있다.

## 7. 채팅방 퇴장

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `DELETE` |
| URL | `/api/regional-chat-rooms/{roomId}/members/me` |
| 인증 | Access Token 필수 |
| 인가 | 해당 채팅방의 활성 참여자 |
| 멱등성 | 퇴장 상태를 만드는 자연 멱등 DELETE |

### 성공 응답

```json
{
  "code": "REGIONAL_CHAT_ROOM_LEFT",
  "message": "지역 채팅방에서 나갔습니다.",
  "data": {
    "roomId": "3001",
    "leftAt": "2026-09-07T20:10:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `REGIONAL_CHAT_ROOM_LEFT` | 퇴장 성공 또는 기존 퇴장 상태 반환 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `404 Not Found` | `REGIONAL_CHAT_ROOM_NOT_FOUND` | 채팅방이 존재하지 않음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 참여 행을 물리 삭제하지 않고 `left_at`을 기록한다.
- 퇴장 이후에는 이력 조회와 새 메시지 송수신을 허용하지 않는다. 다시 입장하면 이후 기능을 다시 사용할 수 있다.

## 8. 메시지 이력 조회

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/regional-chat-rooms/{roomId}/messages` |
| 인증 | Access Token 필수 |
| 인가 | 해당 채팅방의 활성 참여자이며 활성 제재가 없는 사용자 |
| 페이지네이션 | cursor, 최초·추가 요청 모두 20개 고정 |

### 요청

```http
GET /api/regional-chat-rooms/3001/messages?cursor={opaqueCursor}
Authorization: Bearer {accessToken}
```

### 성공 응답

```json
{
  "code": "CHAT_MESSAGES_RETRIEVED",
  "message": "채팅 메시지를 조회했습니다.",
  "data": {
    "items": [
      {
        "messageId": "10001",
        "messageType": "TEXT",
        "text": "경주역 근처 맛집 추천해주세요.",
        "image": null,
        "sender": {
          "publicId": "01991f6e-7300-7b21-a3cc-1436db3df95e",
          "userName": "플랜잇사용자",
          "profileImageUrl": "https://example.invalid/presigned-profile-image"
        },
        "createdAt": "2026-09-07T20:05:00.123456+09:00"
      }
    ],
    "page": {
      "nextCursor": "opaque-older-message-cursor",
      "hasNext": true
    }
  }
}
```

이미지 메시지는 `text=null`이고 `image`에 `imageFileId`, 조회용 `url`, `thumbnailUrl`, `mimeType`을 반환한다. 아직 썸네일이 없으면 `thumbnailUrl=null`이다.

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `CHAT_MESSAGES_RETRIEVED` | 최근 또는 과거 메시지 조회 성공 |
| `400 Bad Request` | `INVALID_CURSOR` | cursor 형식 또는 채팅방 문맥이 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `REGIONAL_CHAT_MEMBER_REQUIRED` | 활성 참여자가 아님 |
| `404 Not Found` | `REGIONAL_CHAT_ROOM_NOT_FOUND` | 채팅방이 존재하지 않음 |
| `423 Locked` | `CHAT_SANCTION_ACTIVE` | 사용자에게 적용 중인 채팅 제재가 있음 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 최초 요청은 최신 `VISIBLE` 메시지 20개, cursor 요청은 그보다 오래된 메시지 20개를 반환한다.
- 응답 `items`는 화면 표시를 위해 오래된 메시지부터 최신 메시지 순으로 정렬한다.
- `BLOCKED`, `DELETED`와 보관기간 90일이 지난 일반 메시지는 반환하지 않는다.
- cursor는 기준 메시지 ID와 채팅방 문맥을 서버만 해석할 수 있는 불투명 값으로 제공한다.
- 조회용 이미지 URL은 권한 확인 후 발급하며 5분 동안 유효하다.

## 9. 채팅 이미지 업로드 요청

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/regional-chat-rooms/{roomId}/image-upload-requests` |
| 인증 | Access Token 필수 |
| 인가 | 해당 채팅방의 활성 참여자이며 활성 제재가 없는 사용자 |
| 멱등성 | `Idempotency-Key` 필수 |

### 요청

```json
{
  "originalFilename": "gyeongju.webp",
  "mimeType": "image/webp",
  "sizeBytes": 3145728
}
```

### 성공 응답

```json
{
  "code": "CHAT_IMAGE_UPLOAD_PREPARED",
  "message": "채팅 이미지 업로드를 준비했습니다.",
  "data": {
    "uploadUrl": "https://example.invalid/presigned-upload-url",
    "uploadKey": "temporary/users/019abc/chat/019def.webp",
    "uploadUrlExpiresAt": "2026-09-07T20:20:00.123456+09:00"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `CHAT_IMAGE_UPLOAD_PREPARED` | 업로드 URL과 임시 객체 Key 발급 성공 |
| `400 Bad Request` | `INVALID_REQUEST` | 파일명, MIME type 또는 크기가 유효하지 않음 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `403 Forbidden` | `REGIONAL_CHAT_MEMBER_REQUIRED` | 활성 참여자가 아님 |
| `404 Not Found` | `REGIONAL_CHAT_ROOM_NOT_FOUND` | 채팅방이 존재하지 않음 |
| `413 Content Too Large` | `CHAT_IMAGE_TOO_LARGE` | 원본 파일이 5 MB를 초과함 |
| `415 Unsupported Media Type` | `UNSUPPORTED_IMAGE_TYPE` | JPEG·PNG·WebP가 아님 |
| `423 Locked` | `CHAT_SANCTION_ACTIVE` | 사용자에게 적용 중인 채팅 제재가 있음 |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | 같은 Key를 다른 요청 내용에 사용함 |
| `409 Conflict` | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | 같은 Key의 요청이 아직 처리 중임 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

### 처리 규칙

- 업로드 URL은 10분 동안 유효하다.
- `uploadKey`는 서버가 현재 사용자와 `CHAT_MESSAGE` 용도에 맞는 S3 임시 영역 경로로 생성하며, 클라이언트가 임의로 변경하지 않는다.
- 클라이언트가 S3 임시 영역에 업로드한 뒤 실시간 이미지 메시지 전송 payload에 `uploadKey`를 전달한다.
- 서버는 로그인 사용자와 `uploadKey`의 소유 경로·용도를 확인하고 S3 객체 존재 여부, 크기와 실제 파일 형식을 검증한 뒤 `image_files.image_purpose=CHAT_MESSAGE`로 확정한다.
- 형식은 JPEG(`image/jpeg`), PNG(`image/png`), WebP(`image/webp`), 크기는 최대 5,242,880 byte다.
- 미제출 임시 객체는 업로드 후 24시간이 지나면 S3 Lifecycle 삭제 대상이 된다.

## 10. 실시간 메시지 계약

| 항목 | 상태 | 현재 결정 |
| --- | --- | --- |
| 전송 기술 | `APPROVED` | Spring WebSocket + STOMP |
| 연결 경로와 인증 전달 방식 | `DRAFT` | 추후 상세 설계에서 결정 |
| 메시지 ACK 형식 | `DRAFT` | 추후 상세 설계에서 결정 |
| 메시지 중복 방지 | `APPROVED` | 제공하지 않음 |
| 재연결 복구 | `APPROVED` | HTTP 메시지 이력 API로 누락 구간 조회 |

실시간 구현은 다음 의미 계약을 유지한다.

- 활성 참여자이며 제재 중이 아닌 사용자만 메시지를 전송·수신할 수 있다.
- 텍스트와 이미지 메시지는 한 메시지에 동시에 포함하지 않는다.
- 이미지 메시지는 업로드 요청 API에서 받은 `uploadKey`를 사용한다.
- 정책 위반 메시지는 `BLOCKED`로 저장하고 다른 사용자에게 전달하지 않는다.
- 제재가 시작되면 모든 지역 채팅 연결에서 사용자를 퇴장시키고 종료 시각과 남은 시간을 알린다.
- 중복 방지 식별자가 없으므로 재전송된 동일 payload도 새 메시지로 처리할 수 있다.

### 기술 선택 근거와 향후 Netty 검토

- 초기 오픈채팅 서비스는 Spring WebSocket + STOMP로 구현한다. Spring의 메시지 처리 기능과 기존 비즈니스 로직을 활용해 구현과 운영의 복잡도를 낮춘다.
- Netty는 초기 구현에 직접 도입하지 않는다. 추후 부하 테스트나 출시 후 모니터링에서 동시 연결 수, 초당 메시지 수, 방별 수신자 수, 메시지 전달 지연과 서버 자원 사용량을 측정한다.
- 부하가 증가하면 DB 처리, 메시지 배포, 네트워크 처리 등 실제 병목을 먼저 확인한다. 네트워크 처리 계층의 병목이나 세밀한 연결·버퍼·스레드 제어의 필요성이 확인되면 Netty 도입을 검토한다.
- 채팅 서버 분리 여부는 Netty 도입과 별도로 판단한다. Netty 사용이 서버 분리를 요구하지 않으며, Spring WebSocket + STOMP 구성도 별도 채팅 애플리케이션으로 분리할 수 있다.
- 이 결정은 구현 방향에 대한 합의이며, 구현 완료나 부하 테스트를 통한 성능 검증을 의미하지 않는다.
