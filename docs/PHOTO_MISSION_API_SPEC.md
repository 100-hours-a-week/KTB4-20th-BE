# PlanIt 포토 미션 API 명세

## 1. 문서 상태와 공통 정책

- 미션 생성 시작과 생성 상태 API는 생성 시점·재시도 정책이 보류되어 `DRAFT`다.
- 나머지 사용자용 HTTP API 9개는 `APPROVED`다.
- 미래 Day의 미션은 생성·조회·수행할 수 없다. 현재 Day와 지난 Day의 미션은 여행 종료일까지 수행할 수 있다.
- 최초 AI 사진 판정은 재판정 횟수에 포함하지 않는다. 이후 정상 완료된 재판정은 최대 3회다.
- AI·네트워크·서버 오류로 판정 결과를 만들지 못한 동작은 재판정 횟수를 차감하지 않는다.
- 정상 완료된 `NEAR`, `FAILED`, `UNRECOGNIZED` 결과는 재판정 횟수에 포함한다.
- 새 사진 제출, 기존 사진 삭제와 날짜 변경으로 재판정 횟수를 초기화하지 않는다.
- 재판정 3회를 모두 사용한 뒤에는 현재 활성 사진으로 본인의 참여를 수동 완료할 수 있다.
- 개인 미션은 본인이 AI 성공 또는 수동 완료해야 완료된다.
- 그룹 미션은 한 명 이상의 참여가 AI 성공 또는 수동 완료이면 그룹 완료다. 멤버별 참여 상태는 별도로 유지한다.
- 그룹 대표 사진은 가장 먼저 AI 성공한 활성 사진이다. 대표 삭제 시 남은 AI 성공 사진 중 제출 시각이 가장 이른 사진을 승격한다.
- 미션·참여·사진·판정·앨범 기록은 여행 종료 후에도 보관한다.

## 2. 포토 미션 여행 목록

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/photo-mission-trips` |
| 인증·인가 | Access Token 본인 |
| 페이지네이션 | 지난 여행만 cursor 기반 최초·추가 10개 |

```json
{
  "code": "PHOTO_MISSION_TRIPS_RETRIEVED",
  "message": "포토 미션 여행을 조회했습니다.",
  "data": {
    "primaryTrip": {
      "tripId": "1001",
      "name": "경주 여행",
      "status": "ONGOING",
      "startDate": "2026-09-07",
      "endDate": "2026-09-09",
      "todayDayNumber": 1
    },
    "pastTrips": {
      "items": [
        {
          "tripId": "901",
          "name": "부산 여행",
          "startDate": "2026-08-01",
          "endDate": "2026-08-03",
          "albumPhotoCount": 12
        }
      ],
      "nextCursor": "opaque-next-cursor",
      "hasNext": true
    }
  }
}
```

- `primaryTrip`은 현재 진행 중인 여행, 없으면 시작일이 가장 가까운 예정 여행이며 둘 다 없으면 `null`이다.
- 여행 날짜 중복 참여가 금지되어 현재 진행 중인 여행은 최대 하나다.
- 지난 여행은 종료일 내림차순, `tripId` 내림차순으로 조회한다.
- 잘못된 cursor는 `400 INVALID_CURSOR`, 인증 실패는 `401 AUTHENTICATION_REQUIRED`를 반환한다.

## 3. 미션 생성 API — 보류

| API | 상태 | 보류 범위 |
| --- | --- | --- |
| `POST /api/trips/{tripId}/mission-generation-jobs` | `DRAFT` | 생성 시작 시점, 내부 자동 시도와 사용자 재요청 정책 |
| `GET /api/mission-generation-jobs/{jobId}` | `DRAFT` | 생성 작업의 단계·실패·재시도 표현 |

취향·현재 Day 일정·날씨를 입력으로 사용하고 동일 Day에 동시에 하나의 작업만 둔다는 모델은 유지하되, 사용자 계약은 보류 정책 확정 후 작성한다.

## 4. Day 미션 목록

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}/photo-missions?date=2026-09-07` |
| 인증·인가 | 해당 여행방 멤버 |
| 페이지네이션 | 없음 |

```json
{
  "code": "PHOTO_MISSIONS_RETRIEVED",
  "message": "포토 미션을 조회했습니다.",
  "data": {
    "tripId": "1001",
    "date": "2026-09-07",
    "dayNumber": 1,
    "generationRequired": false,
    "missions": [
      {
        "missionId": "12001",
        "order": 1,
        "scope": "PERSONAL",
        "title": "황리단길 간판과 함께 사진 찍기",
        "description": "오늘 동선에서 기억에 남는 간판을 찾아보세요.",
        "status": "ACTIVE",
        "myParticipation": {
          "status": "PENDING",
          "completionMethod": null,
          "retryCount": 1,
          "remainingRetries": 2,
          "manualCompletionAllowed": false,
          "activePhotoId": "13001"
        },
        "group": null
      }
    ]
  }
}
```

그룹 미션의 `group`은 `completed`, `completedMemberCount`, `totalMemberCount`, `representativePhotoId`를 반환한다.

- `date`를 생략하면 서울 날짜 기준 오늘을 사용한다.
- 여행 기간 밖이거나 미래 날짜이면 `409 PHOTO_MISSION_DATE_NOT_AVAILABLE`을 반환한다.
- 아직 미션이 없으면 `missions=[]`, `generationRequired=true`를 반환한다. 생성 시작 동작은 보류된 별도 API가 담당한다.
- 인증 실패는 `401`, 여행 멤버가 아니면 `403 TRIP_MEMBER_REQUIRED`, 여행이 없으면 `404 TRIP_NOT_FOUND`다.

## 5. 미션 사진 업로드 요청

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/photo-missions/{missionId}/upload-requests` |
| 인증·인가 | 해당 미션 여행방의 현재 멤버 |
| 멱등성 | `Idempotency-Key` 필수 |

```json
{
  "originalFilename": "mission-photo.jpg",
  "mimeType": "image/jpeg",
  "sizeBytes": 10485760
}
```

```json
{
  "code": "MISSION_PHOTO_UPLOAD_PREPARED",
  "message": "미션 사진 업로드를 준비했습니다.",
  "data": {
    "uploadUrl": "https://example.invalid/presigned-upload-url",
    "uploadKey": "temporary/users/019abc/missions/019def.jpg",
    "uploadUrlExpiresAt": "2026-09-07T21:10:00.123456+09:00"
  }
}
```

- JPEG·PNG·WebP만 허용하고 최대 크기는 15,728,640 byte다.
- 여행 시작 전, 여행 종료 후 또는 미래 Day 미션이면 `409 PHOTO_MISSION_SUBMISSION_CLOSED`다.
- 지원하지 않는 형식은 `415 UNSUPPORTED_IMAGE_TYPE`, 크기 초과는 `413 MISSION_PHOTO_TOO_LARGE`다.
- 업로드 URL은 10분 동안 유효하며 공통 S3 임시 업로드·검증 정책을 따른다.

## 6. 미션 사진 제출·교체

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/photo-missions/{missionId}/photos` |
| 인증·인가 | 해당 여행방 멤버 본인 |
| 멱등성 | `Idempotency-Key` 필수 |

```json
{
  "uploadKey": "temporary/users/019abc/missions/019def.jpg"
}
```

판정 가능 상태의 성공 응답:

```json
{
  "code": "MISSION_PHOTO_EVALUATION_ACCEPTED",
  "message": "사진을 저장하고 AI 판정을 시작했습니다.",
  "data": {
    "photoId": "13001",
    "evaluationStatus": "QUEUED",
    "retryCount": 1,
    "remainingRetries": 2,
    "manualCompletionAllowed": false
  }
}
```

- 최초 정상 판정 전 제출이면 최초 판정이며 `retryCount`를 증가시키지 않는다.
- 최초 판정 이후 새 사진을 제출하면 교체 전 사진을 비활성화하고 새 사진 판정을 재판정으로 취급한다. 정상 결과가 생성된 시점에만 `retryCount`를 증가시킨다.
- 재판정 3회를 이미 사용한 상태에서도 사진 교체는 허용하지만 AI 판정을 시작하지 않는다. 이때 `evaluationStatus=NOT_REQUESTED`, `manualCompletionAllowed=true`와 `200 MISSION_PHOTO_SAVED`를 반환한다.
- `uploadKey`가 현재 사용자·미션·용도에 맞지 않으면 `400 INVALID_UPLOAD_KEY`, S3 임시 객체가 없으면 `404 UPLOADED_IMAGE_NOT_FOUND`, 이미 사용됐으면 `409 IMAGE_ALREADY_USED`다.
- 수행 기간이 아니면 `409 PHOTO_MISSION_SUBMISSION_CLOSED`다.
- 기존 사진 비활성화, 새 `image_files`·`mission_photos` 연결은 원자적으로 처리한다.

## 7. 사진 판정 조회

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/mission-photos/{photoId}/evaluation` |
| 인증·인가 | 해당 여행방 멤버 |

```json
{
  "code": "MISSION_PHOTO_EVALUATION_RETRIEVED",
  "message": "사진 판정 결과를 조회했습니다.",
  "data": {
    "photoId": "13001",
    "status": "SUCCEEDED",
    "evaluation": {
      "evaluationId": "14001",
      "attemptNo": 2,
      "matchScore": 73.5,
      "verdict": "NEAR",
      "recognizedItems": ["간판", "거리"],
      "matchedConditions": ["여행지 간판"],
      "landmark": {
        "name": "황리단길",
        "confidence": 91.2
      },
      "evaluatedAt": "2026-09-07T21:01:00.123456+09:00"
    },
    "retryCount": 1,
    "remainingRetries": 2,
    "manualCompletionAllowed": false
  }
}
```

- `QUEUED`, `RUNNING`, `FAILED`, `NOT_REQUESTED` 상태에서는 `evaluation=null`이다.
- 정상 결과의 `verdict`는 `SUCCESS`, `NEAR`, `FAILED`, `UNRECOGNIZED` 중 하나다.
- AI 시스템 오류는 안전한 공통 실패 상태만 반환하고 내부 오류 원문은 노출하지 않는다.
- 사진이 없거나 삭제됐으면 `404 MISSION_PHOTO_NOT_FOUND`, 멤버가 아니면 `403 TRIP_MEMBER_REQUIRED`다.

## 8. 사진 재판정

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/mission-photos/{photoId}/evaluations` |
| 인증·인가 | 활성 사진 제출자 |
| 멱등성 | `Idempotency-Key` 필수 |

```json
{
  "code": "MISSION_PHOTO_REEVALUATION_ACCEPTED",
  "message": "사진 재판정을 시작했습니다.",
  "data": {
    "photoId": "13001",
    "evaluationStatus": "QUEUED",
    "retryCount": 1,
    "remainingRetries": 2
  }
}
```

- 재판정 작업 접수는 `202 Accepted`다.
- 이전 판정 작업이 실행 중이면 `409 MISSION_PHOTO_EVALUATION_IN_PROGRESS`다.
- 정상 완료된 판정 결과가 이미 하나 이상 존재하는 경우에만 다음 정상 결과를 재판정으로 보아 `retryCount`를 증가시킨다. 최초 정상 결과가 나오기 전 시스템 오류 재시도는 횟수를 유지한다.
- 시스템 오류로 `FAILED`가 되면 정상 결과를 만들지 못했으므로 횟수를 유지한다.
- 재판정 3회를 사용했으면 `409 MISSION_PHOTO_RETRY_LIMIT_EXCEEDED`다.
- 제출자가 아니면 `403 MISSION_PHOTO_OWNER_REQUIRED`, 수행 기간이 끝났으면 `409 PHOTO_MISSION_SUBMISSION_CLOSED`다.

## 9. 미션 수동 완료

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `PUT` |
| URL | `/api/photo-missions/{missionId}/participation/completion` |
| 인증·인가 | 해당 여행방 멤버 본인 |
| 멱등성 | 이미 수동 완료 상태이면 현재 결과를 반환하는 자연 멱등 PUT |

```json
{
  "code": "PHOTO_MISSION_COMPLETED_MANUALLY",
  "message": "미션을 직접 완료했습니다.",
  "data": {
    "missionId": "12001",
    "participationStatus": "COMPLETED",
    "completionMethod": "MANUAL",
    "completedAt": "2026-09-07T21:05:00.123456+09:00",
    "groupCompleted": true
  }
}
```

- 정상 완료된 재판정 3회를 모두 사용했고 현재 삭제되지 않은 활성 사진이 있을 때만 허용한다.
- 조건을 만족하지 않으면 `409 MANUAL_COMPLETION_NOT_ALLOWED`, 수행 기간이 끝났으면 `409 PHOTO_MISSION_SUBMISSION_CLOSED`다.
- AI 성공 상태를 수동 완료로 덮어쓰지 않는다. 이미 AI 완료면 `409 PHOTO_MISSION_ALREADY_COMPLETED`다.
- 그룹 미션이면 수동 완료도 그룹 완료 인원에 포함하지만 AI 성공 사진이 아니므로 대표 사진 후보에는 포함하지 않는다.

## 10. 미션 사진 삭제

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `DELETE` |
| URL | `/api/mission-photos/{photoId}` |
| 인증·인가 | 활성 사진 제출자 |
| 멱등성 | 삭제 상태를 만드는 자연 멱등 DELETE |

```json
{
  "code": "MISSION_PHOTO_DELETED",
  "message": "미션 사진을 삭제했습니다.",
  "data": {
    "photoId": "13001",
    "participationStatus": "PENDING",
    "groupCompleted": true,
    "representativePhotoId": "13005"
  }
}
```

- 사진을 소프트 삭제하고 연결된 이미지 파일을 객체 저장소 정리 대상으로 표시한다.
- 삭제한 사진으로 완료된 참여는 `PENDING`으로 되돌리고 `completion_method`, `completed_at`을 비운다. 재판정 횟수는 유지한다.
- 그룹 미션은 남은 완료 참여가 하나도 없으면 미완료로 바뀐다.
- 대표 사진 삭제 시 남은 AI 성공 활성 사진 중 제출 시각이 가장 이른 사진을 대표로 승격하고 없으면 `null`이다.
- 본인 사진이 아니면 `403 MISSION_PHOTO_OWNER_REQUIRED`, 사진이 없으면 `404 MISSION_PHOTO_NOT_FOUND`다.

## 11. 미션 앨범 조회

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/trips/{tripId}/mission-album` |
| 인증·인가 | 해당 여행방 멤버 |
| 페이지네이션 | cursor 기반 최초·추가 20개 |

```json
{
  "code": "MISSION_ALBUM_RETRIEVED",
  "message": "미션 앨범을 조회했습니다.",
  "data": {
    "items": [
      {
        "photoId": "13001",
        "missionId": "12001",
        "missionTitle": "황리단길 간판과 함께 사진 찍기",
        "missionScope": "PERSONAL",
        "missionDate": "2026-09-07",
        "completionMethod": "AI",
        "representative": false,
        "image": {
          "imageFileId": "15001",
          "url": "https://example.invalid/presigned-image",
          "thumbnailUrl": "https://example.invalid/presigned-thumbnail"
        },
        "landmark": {
          "name": "황리단길",
          "confidence": 91.2
        },
        "submittedAt": "2026-09-07T21:00:00.123456+09:00"
      }
    ],
    "page": {
      "nextCursor": "opaque-next-cursor",
      "hasNext": true
    }
  }
}
```

- 완료된 참여의 현재 활성 사진만 최신 제출 순으로 반환한다.
- 삭제·교체된 사진과 미완료 참여 사진은 앨범에서 제외한다.
- 이미지 조회 URL은 5분 동안 유효하며 썸네일 생성 전이면 `thumbnailUrl=null`이다.
- cursor가 유효하지 않으면 `400 INVALID_CURSOR`, 멤버가 아니면 `403 TRIP_MEMBER_REQUIRED`, 여행이 없으면 `404 TRIP_NOT_FOUND`다.
- 회원 탈퇴로 사진이 삭제된 경우에도 남은 완료·대표 상태를 재계산한 최신 결과를 반환한다.
