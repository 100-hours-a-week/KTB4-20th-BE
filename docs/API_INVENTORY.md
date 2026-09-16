# PlanIt 전체 API 목록 초안

## 1. 목적과 상태

이 문서는 상세 API 명세를 작성하기 전에 서비스 전체 API의 범위와 도메인 경계를 합의하기 위한 인벤토리다.

- 기준 문서: [`FIGMA_FINAL_BUSINESS_RULES.md`](../FIGMA_FINAL_BUSINESS_RULES.md)의 최종 7개 도메인과 후속 정제 정책
- 데이터 모델 참고: [`planit_consolidated.sql`](../erd/planit_consolidated.sql). 테이블과 관계를 API 후보 도출에 참고하되 DDL의 ID 방식·타입·제약조건을 API 계약으로 자동 채택하지 않는다.
- 상세 형식: [`API_SPEC_GUIDELINES.md`](./API_SPEC_GUIDELINES.md)
- 인증 상세 계약: [`API_SPEC.md`](./API_SPEC.md)
- 여행 상세 계약: [`TRIP_API_SPEC.md`](./TRIP_API_SPEC.md)
- 설문·일정 상세 계약: [`SCHEDULE_API_SPEC.md`](./SCHEDULE_API_SPEC.md)
- 지역 오픈 채팅 상세 계약: [`CHAT_API_SPEC.md`](./CHAT_API_SPEC.md)
- 커뮤니티 상세 계약: [`COMMUNITY_API_SPEC.md`](./COMMUNITY_API_SPEC.md)
- 포토 미션 상세 계약: [`PHOTO_MISSION_API_SPEC.md`](./PHOTO_MISSION_API_SPEC.md)
- 알림 상세 계약: [`NOTIFICATION_API_SPEC.md`](./NOTIFICATION_API_SPEC.md)
- 백엔드 구현이 없으므로 `IMPLEMENTED`, `VERIFIED` 상태를 사용하지 않는다.
- 개별 검토가 완료된 계약만 `APPROVED`로 표시하며, 아직 상세화하지 않은 Method와 URL은 `DRAFT`로 유지한다.
- 화면 하나와 API 하나를 일대일로 대응시키지 않는다. 한 화면에 필요한 일관된 집계는 하나의 조회 응답으로 묶을 수 있다.

## 2. 범위 요약

| 도메인 | 사용자용 HTTP API | 별도 실시간 인터페이스 | 주요 책임 |
| --- | ---: | ---: | --- |
| 인증, 인가 | 7 | 0 | 카카오 로그인, 토큰, 현재 사용자, 로그아웃·탈퇴 |
| 여행 | 8 | 0 | 지역, 여행방, 초대, 참여와 멤버 관리 |
| 설문 기반 일정 생성 | 16 | 0 | 설문, 장소 검색·선택, AI 생성, 일정 확정·수정, 날씨 결정 |
| 오픈 채팅 | 7 | 1 | 지역 채팅방, 정책 동의, 입퇴장, 메시지·이미지 |
| 커뮤니티 | 5 | 0 | 완료 일정 조회, 조회 집계와 좋아요 |
| 포토 미션 | 11 | 0 | 미션 생성·조회, 사진 제출·판정·삭제, 앨범 |
| 알림 | 4 | 0 | 목록, 미읽음 수, 개별·전체 읽음 |
| **합계** | **58** | **1** |  |

숫자는 상세 계약 검토에서 API를 합치거나 나누면 변경될 수 있다. 서버 내부 스케줄러·작업자 인터페이스는 포함하지 않는다.

## 3. 인증, 인가

| API | Method | URL | 목적 | 인증 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 카카오 로그인 시작 | `GET` | `/api/auth/kakao/authorize` | 카카오 인가 화면으로 이동 | 불필요 | `APPROVED` |
| 카카오 OAuth callback | `GET` | `/api/auth/kakao/callback` | 인가 코드 교환 및 사용자 로그인 | OAuth `state` | `APPROVED` |
| JWT Public Key 조회 | `GET` | `/.well-known/jwks.json` | JWT 검증용 JWK Set 제공 | 불필요 | `APPROVED` |
| Access Token 갱신 | `POST` | `/api/auth/refresh` | Refresh Token으로 Access Token 발급 | Refresh Token Cookie | `APPROVED` |
| 로그아웃 | `POST` | `/api/auth/logout` | 현재 Refresh Token 폐기 | Refresh Token Cookie | `APPROVED` |
| 현재 사용자 조회 | `GET` | `/api/users/me` | 로그인 후 사용자 상태 복원 | Access Token | `APPROVED` |
| 회원 탈퇴 | `DELETE` | `/api/users/me` | 카카오 연결 해제와 사용자 소프트 삭제 | Access Token | `APPROVED` |

현재 사용자 조회는 최소한 `publicId`, 카카오에서 가져와 `users.username`에 저장한 `userName`, 프로필 이미지 조회용 URL을 반환하는 후보로 둔다. 카카오 프로필 이미지는 `users.image_file_id`로 `image_files`에 연결하고, 값이 없으면 기본 이미지를 사용한다. 정확한 응답 필드는 인증 묶음의 남은 계약에서 확정한다.

## 4. 여행

| API | Method | URL | 목적 | 인증·인가 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 서비스 지역 목록 | `GET` | `/api/regions` | 사전 등록된 광역·하위 지역 선택지 조회 | Access Token | `APPROVED` |
| 내 여행 목록 | `GET` | `/api/trips` | 참여 중·예정·지난 여행을 cursor로 10개씩 조회 | Access Token | `APPROVED` |
| 여행방 생성 | `POST` | `/api/trips` | 기존 여행과 겹치지 않는 여행 정보 저장 및 생성자를 방장으로 등록 | Access Token | `APPROVED` |
| 여행방 상세 조회 | `GET` | `/api/trips/{tripId}` | 여행 정보, 활성 멤버와 설문 진행률 조회 | 여행 멤버 | `APPROVED` |
| 초대 링크 조회 | `GET` | `/api/trips/{tripId}/invitation` | 여행방의 고정 공유용 초대 토큰 조회 | 방장 | `APPROVED` |
| 초대 정보 확인 | `GET` | `/api/invitations/{invitationToken}` | 로그인 후 참여 전 여행·초대자·정원 상태 확인 | Access Token | `APPROVED` |
| 초대 참여 | `POST` | `/api/invitations/{invitationToken}/join` | 정원·설문·여행 기간 중복 확인 후 멤버십 생성 | Access Token | `APPROVED` |
| 여행방 나가기 | `DELETE` | `/api/trips/{tripId}/members/me` | 여행 시작 전에 현재 멤버의 참여 종료 | 여행 멤버 | `APPROVED` |

서비스 지원 지역은 운영 전에 `regions` 테이블에 미리 등록한다. `regionId`는 광역 코드나 하위 지역 코드가 아니라 광역·하위 정보를 함께 가진 여행 지역 행의 PK인 `regions.id`다. 목록 응답은 화면을 위해 광역 지역별로 그룹화하지만 여행방 생성 요청은 최종 선택한 `regionId` 하나만 전달한다. 기준정보가 비어 있으면 빈 배열을 정상 응답하지 않고 `503 REGION_CATALOG_UNAVAILABLE`을 반환한다. 여행방 생성 요청에서 지역을 누락하면 `400 INVALID_REQUEST`, 존재하지 않는 ID이면 `404 REGION_NOT_FOUND`를 반환한다.

멤버 내보내기와 방장의 직접 여행방 삭제 기능은 제공하지 않는다. 여행방 나가기는 서울 날짜 기준 여행 시작 전까지만 허용한다. 생성 직후 방장만 있는 여행방은 방장이 남아 있는 동안 초대를 위해 유지하지만, 마지막 방장이 나가면 소유자 없는 방을 남기지 않고 소프트 삭제한다. 여행 시작 전에 활성 멤버가 한 번이라도 2명 이상이 된 여행방이 나가기·회원 탈퇴로 1명만 남으면 여행방을 소프트 삭제한다. 이때 남은 멤버십도 같은 시각에 `left_at`을 기록하고 비활성화하며, 멤버십 이력은 물리 삭제하지 않는다. 활성 멤버가 2명 이상 남은 상태에서 방장이 나가거나 탈퇴하면 `joined_at`이 가장 빠른 활성 멤버에게 방장 역할을 이전한다. 회원 탈퇴 자체는 여행 중·후에도 허용하지만 이로 인한 인원 감소로 진행 중·종료 여행방을 자동 삭제하지 않는다. 여행 시작 전 여행방이 자동 삭제되면 일정과 하위 Day·장소·이동 구간을 애플리케이션에서 함께 정리한다. 진행 중인 AI 생성 작업은 취소하지 않으며, 완료되더라도 결과를 삭제된 여행방에 반영하지 않는다. 포토 미션 사진은 여행 시작 전 제출할 수 없으므로 이 연쇄 정리 대상에 포함되지 않는다.

초대 참여는 초대 Token이 유효하고, 여행방이 삭제되지 않았으며, 여행 시작 전이고, 신규 멤버 참여가 열려 있고, 활성 인원이 정원보다 적으며, 요청 사용자가 아직 참여하지 않고 해당 사용자의 다른 활성 여행과 날짜가 겹치지 않는 경우에만 허용한다. 신규 멤버 참여는 `survey_deadline_at`이 지났거나 AI 일정 생성 작업이 시작되면 종료한다. 모든 기존 멤버가 설문을 제출한 것만으로는 종료하지 않으며, AI 생성 시작 전에 새 멤버가 참여하면 해당 멤버를 포함해 설문 진행률을 다시 계산한다. 마지막 설문 상태 변경·AI 생성 시작과 초대 참여가 동시에 요청되면 먼저 확정된 상태를 기준으로 다른 요청을 처리한다.

여행방에는 만료 시각이 없는 고정 초대 토큰 하나를 사용한다. 토큰은 256비트 보안 난수로 생성해 Base64 URL-safe 무패딩 문자열로 인코딩하고, 동일 링크 재조회를 위해 `trip_invitations.token`에 원문을 저장한다. 초대 링크 조회만으로 토큰을 다시 생성하지 않으며, 일반 API 응답과 로그에는 노출하지 않는다. 현재 방장만 토큰을 조회·공유할 수 있고 여행방이 소프트 삭제되면 토큰도 즉시 무효로 취급한다. 전체 URL은 저장하지 않으며 클라이언트가 현재 frontend origin과 토큰을 조합해 공유 링크를 만든다. 참여 화면의 `초대한 사용자`는 별도 query나 서명 없이 현재 방장의 사용자 정보를 표시한다. 방장이 위임되면 기존 링크에서도 새 방장을 초대한 사용자로 표시한다.

비로그인 사용자가 초대 링크에 접근하면 초대 정보를 조회하지 않고 로그인 페이지로 이동한다. frontend는 `/invitations/{invitationToken}`을 검증된 `returnTo`로 전달하고, 로그인 또는 최초 사용자 생성이 완료되면 서버는 해당 초대 페이지로 다시 이동시킨다. 초대 정보 확인과 참여는 모두 Access Token이 필요하다.

여행방 이름·지역·시작일·종료일·정원·설문 마감 시각은 생성 후 수정할 수 없으며 여행방 정보 수정 API를 제공하지 않는다. 잘못 생성한 여행방은 여행 시작 전에 구성원이 각자 나가 자동 삭제되게 한 뒤 다시 생성한다. 설문 재제출과 확정 일정의 장소 추가·삭제는 여행방 기본 정보 수정과 별개의 기능으로 유지한다.

여행 시작일은 서울 날짜 기준 최소 내일이고, 시작일과 종료일을 포함해 1일부터 최대 10일까지 허용한다. 설문 마감 날짜는 오늘부터 여행 시작일 전날까지이며 선택한 날짜의 `23:59:59.999999`로 저장한다. `1일 뒤`와 `3일 뒤`가 여행 시작일 이상이면 해당 선택지를 제공하지 않는다.

여행 기간은 시작일과 종료일을 모두 포함하며 `기존 시작일 <= 요청 종료일 AND 기존 종료일 >= 요청 시작일`이면 겹치는 것으로 판단한다. 소프트 삭제된 여행방과 `left_at`이 기록된 비활성 멤버십은 중복 검사에서 제외한다. 여행방 생성과 초대 참여가 동시에 요청되면 먼저 확정된 여행만 성공한다.

| 초대·참여 실패 HTTP | API 코드 | 화면 또는 처리 |
| ---: | --- | --- |
| `404 Not Found` | `INVITATION_NOT_FOUND` | 잘못된 초대 링크 오류 화면 |
| `410 Gone` | `INVITATION_TRIP_DELETED` | 삭제된 여행방 오류 화면 |
| `409 Conflict` | `TRIP_SURVEY_CLOSED` | 설문 마감 또는 AI 생성 시작으로 신규 참여 불가 |
| `409 Conflict` | `TRIP_CAPACITY_EXCEEDED` | 정원 초과로 참여 불가 |
| `409 Conflict` | `TRIP_ALREADY_STARTED` | 여행 시작으로 참여 불가 |
| `409 Conflict` | `TRIP_ALREADY_JOINED` | 이미 참여 중인 여행방으로 이동 |
| `409 Conflict` | `TRIP_DATE_CONFLICT` | 다른 활성 여행과 날짜가 겹쳐 생성·참여 불가 |

초대 정보 확인과 초대 참여 API는 동일한 참여 가능 조건과 오류 코드를 사용한다. 나가기 성공은 `200 OK`와 `TRIP_MEMBERSHIP_ENDED`를 반환하며 `data.tripDeleted`와 `data.newHostMemberId`로 자동 삭제와 방장 이전 결과를 전달한다. 여행 중·후 나가기는 `409 TRIP_LEAVE_NOT_ALLOWED`를 반환한다.

## 5. 설문 기반 일정 생성

| API | Method | URL | 목적 | 인증·인가 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 취향 문항 목록 | `GET` | `/api/preference-questions` | 고정 5점 문항과 일정 제외 카테고리 조회 | Access Token | `DRAFT` |
| 지역 장소 검색 | `GET` | `/api/regions/{regionId}/places` | 선택 지역의 광역 지역명을 검색어에 결합해 Google Places 장소를 cursor로 10개씩 검색 | Access Token | `APPROVED` |
| 선택 장소 저장 | `POST` | `/api/regions/{regionId}/places` | 사용자가 클릭한 Google Places 장소 하나만 저장하고 내부 장소 ID 반환 | Access Token | `APPROVED` |
| 장소 상세 조회 | `GET` | `/api/places/{placeId}` | 주소·좌표·카테고리·연락처 조회 | Access Token | `APPROVED` |
| 내 설문 조회 | `GET` | `/api/trips/{tripId}/survey` | 기존 답변, 제외 카테고리와 꼭 가고 싶은 장소 조회 | 여행 멤버 본인 | `APPROVED` |
| 내 설문 제출·수정 | `PUT` | `/api/trips/{tripId}/survey` | 답변, 제외 카테고리와 꼭 가고 싶은 장소를 원자적으로 교체 | 여행 멤버 본인 | `APPROVED` |
| 설문 현황·집계 조회 | `GET` | `/api/trips/{tripId}/survey-summary` | 제출률과 그룹 취향 집계 조회 | 여행 멤버 | `APPROVED` |
| AI 일정 생성 시작 | `POST` | `/api/trips/{tripId}/schedule-generation-jobs` | 설문 스냅샷으로 비동기 생성 작업 시작 | 방장 | `APPROVED` |
| AI 일정 생성 상태 | `GET` | `/api/schedule-generation-jobs/{jobId}` | 생성 단계·성공·실패 상태 복구 | 여행 멤버 | `APPROVED` |
| 후보 일정 목록 | `GET` | `/api/schedule-generation-jobs/{jobId}/candidates` | 세 가지 전략의 후보 일정 조회 | 여행 멤버 | `APPROVED` |
| 일정 확정 | `PUT` | `/api/trips/{tripId}/schedule` | 후보 일정 하나를 현재 확정 일정으로 선택 | 방장 | `APPROVED` |
| 확정 일정 조회 | `GET` | `/api/trips/{tripId}/schedule` | 현재 일정의 Day·방문 순서·좌표 간 거리 조회 | 여행 멤버 | `APPROVED` |
| 일정 장소 추가 | `POST` | `/api/trips/{tripId}/schedule/stops` | 원하는 순서에 장소를 삽입하고 인접 구간 거리만 갱신 | 방장 | `APPROVED` |
| 일정 장소 삭제 | `DELETE` | `/api/trips/{tripId}/schedule/stops/{stopId}` | 장소를 제거하고 인접 구간 거리만 갱신 | 방장 | `APPROVED` |
| 최근 날씨 점검 조회 | `GET` | `/api/trips/{tripId}/weather-checks/latest` | 날씨 영향과 결정·대체 작업 상태 조회 | 여행 멤버 | `APPROVED` |
| 날씨 일정 결정 | `PUT` | `/api/weather-checks/{weatherCheckId}/decision` | 기존 일정 유지 또는 대체 일정 생성·실패 후 재시도 | 방장 | `APPROVED` |

AI 작업 재접속은 여행방 상세가 활성 `jobId`를 제공하고 생성 상태 API를 다시 호출하는 구조를 우선 후보로 둔다. 별도 `active-job` endpoint는 중복을 피하기 위해 목록에 추가하지 않았다.

장소 검색 시 클라이언트는 사용자가 입력한 검색어만 전달한다. 서버는 `regionId`로 조회한 `regions.broad_region_name`을 검색어 앞에 공백으로 결합해 Google Places API를 호출한다. 예를 들어 사용자가 `카페`를 입력하고 광역 지역명이 `부산광역시`이면 실제 외부 API 질의어는 `부산광역시 카페`다. 추가된 지역명은 검색창과 응답에 노출하지 않는다. 좌표나 행정구역 코드로 결과를 사후 제외하지 않으므로 이 정책은 특정 행정구역 포함을 보장하는 하드 필터가 아니라 지역 관련도를 높이는 검색 방식이다. 선택한 장소를 저장할 때 `places.region_id`에는 실제 행정구역 판정값이 아니라 검색에 사용한 여행 지역의 `regions.id`를 기록한다.

사용자 한 명이 한 여행의 설문에 선택할 수 있는 꼭 가고 싶은 장소는 최대 10개이며 0개도 허용한다. 같은 Google Places 장소를 동일 설문에 중복 선택할 수 없고, 수정 제출 시에도 최종 선택 목록 전체에 같은 제한을 적용한다. 검색 결과 페이지네이션 개수와 장소 선택 한도는 별개다.

모든 취향 문항은 화면 진입 시 중립값 `3`으로 선택된 상태다. 설문 제출 시 각 문항의 최종 선택값 `1~5`를 빠짐없이 전달하고, 서버는 별도 가중치·정규화 없이 `survey_answers.score`에 그대로 저장한다. 그룹 결과도 0~100%로 환산하지 않고 제출 완료된 활성 멤버의 원본 점수를 카테고리별로 산술 평균해 `1.0~5.0` 범위, 소수 첫째 자리로 제공한다.

설문을 제출한 활성 멤버는 다시 제출할 수 있다. 재제출은 기존 답변, 제외 카테고리와 꼭 가고 싶은 장소 전체를 원자적으로 교체하고 최신 설문 집계에 즉시 반영한다. AI 생성 중에 재제출해도 이미 실행 중인 작업은 시작 시점의 `schedule_generation_snapshots`만 사용하므로 해당 작업의 생성 결과에는 영향을 주지 않는다. AI 생성 시작은 신규 멤버 참여를 종료하지만 기존 멤버의 재제출을 그 자체로 차단하지 않는다.

설문을 한 번도 제출하지 않은 멤버의 최초 제출은 `survey_deadline_at`까지 허용한다. 마감 전에 제출을 완료한 멤버의 재제출은 설문 마감이나 AI 생성 상태와 관계없이 서울 날짜 기준 여행 시작 전까지만 허용한다. 최초 제출하지 않은 멤버는 마감 후 재제출 규칙을 이용해 새로 제출할 수 없다. 여행 시작일부터는 모든 재제출을 종료한다.

Google Places 검색 결과 전체를 `places`에 저장하지 않는다. 사용자가 결과를 클릭하면 클라이언트가 선택된 Google Places 장소의 식별값과 표시 정보를 `POST /api/regions/{regionId}/places`로 전달하고, 서버는 `(regionId, googlePlaceId)` 기준으로 해당 장소 하나만 upsert해 내부 `placeId`를 반환한다. 별도 후보 Token은 사용하지 않는다. 서버는 필드 형식·길이·좌표 범위를 검증하지만 클라이언트가 보낸 값이 직전 Google Places 응답과 동일한지 암호학적으로 증명하지는 않는 현재 MVP 계약이다.

일정 제외 항목은 장소가 아니라 사전 등록된 카테고리이며 여러 개를 선택할 수 있다. Spring 서버는 AI 일정 생성 시작 시 제출 완료된 활성 멤버들의 원본 5점 점수, 선택된 제외 카테고리 코드와 꼭 가고 싶은 장소 정보를 불변 스냅샷으로 저장한 뒤 FastAPI 서버에 전달한다. 클라이언트는 FastAPI를 직접 호출하지 않는다.

Spring 백엔드는 전달할 장소를 자체 추천하거나 제외 카테고리와 꼭 가고 싶은 장소 사이의 충돌을 해소하지 않는다. 장소 추천, 포함·제외 여부, 경로 구성과 제외 사유 생성은 FastAPI가 전담한다. Spring은 요청 데이터 검증, 스냅샷 저장, FastAPI 작업 요청, 결과 저장과 사용자 API 제공만 담당한다.

AI 일정은 이동수단과 소요 시간을 고려하지 않고 장소 좌표 간 거리를 기준으로 방문 순서를 계산한다. FastAPI는 음식점끼리 또는 카페끼리 연속 배치하지 않으며 `음식점 → 카페 → 음식점`, `카페 → 음식점 → 카페`도 만들지 않는다. 장소 대체와 생성 가능 여부는 FastAPI가 판단하고 Spring 서버는 조건을 만족한 장소와 경로가 항상 반환된다고 가정한다.

FastAPI 통신 오류와 같은 일시적 실패를 동일 `schedule_generation_job` 내부에서 자동 재시도할 때는 최초 스냅샷을 그대로 사용한다. 자동 재시도가 모두 실패해 작업이 최종 `FAILED`가 된 뒤 방장이 다시 생성 요청을 보내면 새 작업을 만들고, 재요청 시점의 최신 제출 설문으로 새 스냅샷을 생성한다. 이전 실패 작업과 스냅샷은 이력으로 유지한다.

방장은 일정 확정 전에 성공한 최초 결과 이후 사용자 재생성을 최대 3회까지 완료할 수 있어 성공한 일반 AI 일정 결과는 최대 4개다. 최종 `FAILED` 작업은 횟수를 차감하지 않고 같은 성공 결과 순서로 다시 요청할 수 있다. 시스템 내부 자동 재시도와 날씨 기반 대체 일정 생성도 사용자 재생성 횟수에 포함하지 않는다. 새 생성 작업이 성공하면 이전 작업의 선택되지 않은 후보 일정과 그 하위 데이터를 삭제하고 작업·입력 스냅샷 이력은 유지한다. 일정이 한 번 확정되어 `ACTIVE` 일정이 존재하면 일반 사용자 재생성을 더 이상 허용하지 않으며, 확정 일정의 장소 추가·삭제와 D-1 날씨 기반 대체 일정만 별도 정책으로 허용한다.

방장은 여행 시작 전날 `23:59:59.999999+09:00`까지만 후보 일정 재확정과 확정 일정의 장소 추가·삭제를 할 수 있다. 장소는 선택한 Day의 처음, 두 장소 사이 또는 마지막에 삽입할 수 있다. 별도 순서 변경과 Day 간 장소 이동은 제공하지 않는다. 장소 추가·삭제 시 FastAPI나 AI가 방문 순서를 재구성하지 않고, 백엔드가 변경된 인접 장소의 좌표 간 거리만 갱신한다. 수동 추가·삭제에는 음식점·카페 배치 제약을 적용하지 않는다. 방장이 날씨 변경 여부에 응답하지 않으면 여행 시작 시 시스템이 기존 일정을 유지하는 `KEEP` 결정을 기록하며, 이때 `decided_by_member_id`는 `NULL`이다. 방장이 `REGENERATE`를 선택하면 날씨 기반 대체 일정 생성에 성공한 시점에 별도 재확인 없이 새 일정을 현재 일정으로 자동 반영하고, 생성 실패 시 기존 일정을 유지한다.

날씨 `KEEP`는 최종 결정이며 이후 수정할 수 없다. `REGENERATE`가 최종 실패하면 사용자 횟수를 차감하지 않고 여행 시작 전까지 다시 요청할 수 있다. 동시에 하나의 대체 작업만 실행하며 성공하면 새 일정을 자동 적용하고 추가 날씨 재생성을 막는다. 여행 시작 후에는 새 결정과 재시도를 받지 않는다. 시작 전에 실행된 작업이 시작 후 완료되면 새 일정을 저장·활성화하지 않고 `FAILED`, 내부 `WEATHER_REPLAN_EXPIRED`로 종료하며 기존 확정 일정을 유지한다.

## 6. 오픈 채팅

| API | Method | URL | 목적 | 인증·인가 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 지역 채팅방 목록 | `GET` | `/api/regional-chat-rooms` | 관련 여행 지역 우선·참여자 수 순으로 조회 | Access Token | `APPROVED` |
| 현재 채팅 정책 조회 | `GET` | `/api/chat-policy` | 입장 전 적용 정책과 동의 상태 조회 | Access Token | `APPROVED` |
| 채팅 정책 동의 | `PUT` | `/api/chat-policy/consent` | 현재 정책 버전에 동의 | Access Token | `APPROVED` |
| 채팅방 입장 | `PUT` | `/api/regional-chat-rooms/{roomId}/members/me` | 정책·제재 확인 후 현재 사용자 입장 | Access Token | `APPROVED` |
| 채팅방 퇴장 | `DELETE` | `/api/regional-chat-rooms/{roomId}/members/me` | 현재 사용자의 채팅방 참여 종료 | 채팅방 참여자 | `APPROVED` |
| 메시지 이력 조회 | `GET` | `/api/regional-chat-rooms/{roomId}/messages` | 최근 메시지부터 cursor로 20개씩 조회 | 채팅방 참여자 | `APPROVED` |
| 채팅 이미지 업로드 요청 | `POST` | `/api/regional-chat-rooms/{roomId}/image-upload-requests` | JPEG·PNG·WebP, 최대 5MB 업로드 준비 | 채팅방 참여자 | `APPROVED` |

### 6.1 실시간 인터페이스 후보

| 인터페이스 | 방식 | 경로 | 목적 | 상태 |
| --- | --- | --- | --- | --- |
| 지역 채팅 연결·메시지 | WebSocket 또는 Socket.IO 미정 | `/ws/regional-chat` | 방 구독, 텍스트·이미지 메시지 전송과 수신, 제재 통지 | `DRAFT` |

HTTP는 방·정책·참여·과거 이력·이미지 업로드를 담당하고 실시간 인터페이스는 새 메시지와 제재 통지를 담당한다. 재연결 시 누락 메시지는 HTTP 이력 API로 복구한다. 실시간 기술과 ACK 계약은 추후 확정하며 `clientMessageId`와 메시지 중복 방지는 사용하지 않는다.

새 채팅 정책 버전이 활성화되면 다시 동의해야 한다. 정책 위반 메시지는 `BLOCKED`로 저장하지만 다른 사용자에게 전송하거나 일반 이력에 노출하지 않는다. 위반 3회마다 제재를 적용하고 다음 제재 판단용 위반 횟수는 다시 0부터 계산하되 위반·제재 이력과 제재 단계는 유지한다. 제재는 사용자 전체에 적용되어 모든 지역 채팅방 이용을 막으며, 종료 후 최신 정책에 동의한 상태라면 다시 입장할 수 있다. 일반 공개 메시지는 90일 후 삭제하고 위반·제재에 연결된 차단 메시지는 보존한다.

## 7. 커뮤니티

| API | Method | URL | 목적 | 인증·인가 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 커뮤니티 일정 목록 | `GET` | `/api/community/posts` | 전체·지역 필터와 인기순 목록을 cursor로 10개씩 조회 | Access Token | `APPROVED` |
| 커뮤니티 일정 상세 | `GET` | `/api/community/posts/{postId}` | 공개된 일정과 동선 상세 조회 | Access Token | `APPROVED` |
| 게시물 조회 기록 | `POST` | `/api/community/posts/{postId}/views` | 사용자·게시물별 전체 기간 1회만 조회수 반영 | Access Token | `APPROVED` |
| 좋아요 등록 | `PUT` | `/api/community/posts/{postId}/like` | 현재 사용자의 좋아요를 멱등 등록 | Access Token | `APPROVED` |
| 좋아요 취소 | `DELETE` | `/api/community/posts/{postId}/like` | 현재 사용자의 좋아요를 멱등 취소 | Access Token | `APPROVED` |

커뮤니티는 로그인 사용자만 접근할 수 있다. 대표 인기 일정은 목록 API의 정렬·필터 결과 첫 항목으로 제공하며 별도 endpoint를 만들지 않는다. 여행 종료 시점에 당시 확정 일정 버전을 자동 공개하고 별도 공개 동의나 비공개 선택은 제공하지 않는다. 조회수는 날짜가 바뀌어도 사용자·게시물 조합당 전체 기간 한 번만 반영한다. 게시물과 공개 일정은 별도 삭제 정책 없이 계속 보관한다.

## 8. 포토 미션

| API | Method | URL | 목적 | 인증·인가 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 포토 미션 여행 목록 | `GET` | `/api/photo-mission-trips` | 진행 중·가장 가까운 여행과 지난 여행을 cursor로 10개씩 조회 | Access Token | `APPROVED` |
| 일별 미션 생성 시작 | `POST` | `/api/trips/{tripId}/mission-generation-jobs` | 일정·취향·날씨 기반 비동기 생성 시작 | 여행 멤버 | `DRAFT` |
| 미션 생성 상태 | `GET` | `/api/mission-generation-jobs/{jobId}` | 생성 단계와 실패·재시도 상태 조회 | 여행 멤버 | `DRAFT` |
| Day 미션 목록 | `GET` | `/api/trips/{tripId}/photo-missions` | Day별 개인·그룹 미션과 완료 상태 조회 | 여행 멤버 | `APPROVED` |
| 미션 사진 업로드 요청 | `POST` | `/api/photo-missions/{missionId}/upload-requests` | JPEG·PNG·WebP, 최대 15MB 업로드 준비 | 여행 멤버 | `APPROVED` |
| 미션 사진 제출 | `POST` | `/api/photo-missions/{missionId}/photos` | 업로드 완료 등록·기존 사진 대체·AI 판정 시작 | 여행 멤버 본인 | `APPROVED` |
| 사진 판정 조회 | `GET` | `/api/mission-photos/{photoId}/evaluation` | 점수·판정·인식 항목·랜드마크 조회 | 여행 멤버 | `APPROVED` |
| 사진 재판정 | `POST` | `/api/mission-photos/{photoId}/evaluations` | AI 판정 재시도 | 사진 제출자 | `APPROVED` |
| 미션 수동 완료 | `PUT` | `/api/photo-missions/{missionId}/participation/completion` | 재시도 한도 초과 후 본인 미션 수동 완료 | 여행 멤버 본인 | `APPROVED` |
| 미션 사진 삭제 | `DELETE` | `/api/mission-photos/{photoId}` | 본인 사진 삭제와 완료·대표 상태 재계산 | 사진 제출자 | `APPROVED` |
| 미션 앨범 조회 | `GET` | `/api/trips/{tripId}/mission-album` | 여행 미션 완료 사진과 랜드마크를 cursor로 20개씩 조회 | 여행 멤버 | `APPROVED` |

채팅 이미지, 미션 사진과 프로필 이미지는 S3에 저장하고 객체 키·용도와 메타데이터는 `image_files`에서 관리한다. 클라이언트 업로드는 서버가 생성한 임시 `uploadKey`와 Presigned URL을 사용하고, 도메인 제출 시 `uploadKey`로 S3 객체를 검증한 후 생성한 `image_file_id`를 사용자·채팅 메시지·미션 사진에 연결한다. 카카오 프로필 이미지는 백엔드가 직접 저장하므로 클라이언트 업로드 절차를 사용하지 않는다. 업로드 URL은 10분, 조회 URL은 5분 동안 유효하며 만료 시 새로 발급한다. JPEG·PNG·WebP만 허용하고 최대 크기는 프로필 5 MB, 채팅 5 MB, 미션 사진 15 MB로 제한한다. 실제 객체의 형식과 크기는 연결 전에 서버가 검증한다. 클라이언트 업로드 객체는 임시 영역에 저장하며, 제출·검증되지 않은 객체는 24시간 후 S3 Lifecycle 삭제 대상으로 처리한다. 확정 시 EXIF 방향을 반영하고 GPS 등 메타데이터를 제거한 영구 이미지를 저장하며, 목록용 WebP 썸네일은 최대 512×512로 비동기 생성한다.

포토 미션 최초 사진 판정은 재판정 횟수에 포함하지 않고 이후 정상 완료된 재판정을 최대 3회 허용한다. 시스템 오류로 결과를 만들지 못한 동작은 차감하지 않으며 새 사진 제출·삭제 후에도 이미 사용한 횟수는 초기화하지 않는다. 한도를 사용한 뒤 본인 참여를 수동 완료할 수 있다. 그룹 미션은 한 명 이상이 AI 성공 또는 수동 완료하면 그룹 완료이며, 대표 사진은 가장 먼저 AI 성공한 활성 사진으로 정한다. 미션 기록은 여행 종료 후에도 보관하고 회원 탈퇴·사진 삭제 시 남은 사진을 기준으로 완료·대표 상태를 다시 계산한다. 미션 생성 시작·상태 정책은 보류 상태다.

## 9. 알림

| API | Method | URL | 목적 | 인증·인가 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 알림 목록 | `GET` | `/api/notifications` | 최신순으로 최초 20개, 이후 cursor로 10개씩 조회 | Access Token 본인 | `APPROVED` |
| 미읽음 알림 수 | `GET` | `/api/notifications/unread-count` | 알림 표시점과 미읽음 수 조회 | Access Token 본인 | `APPROVED` |
| 알림 읽음 | `PUT` | `/api/notifications/{notificationId}/read` | 개별 알림을 멱등하게 읽음 처리 | Access Token 본인 | `APPROVED` |
| 알림 모두 읽음 | `PUT` | `/api/notifications/read-all` | 현재 사용자의 모든 알림을 읽음 처리 | Access Token 본인 | `APPROVED` |

날씨 조회, 알림 생성과 중복 방지는 서버 내부 작업이다. 푸시 알림은 동의·발송 정책이 정해지지 않아 목록에서 제외했다.

알림은 생성 후 90일이 되면 `deleted_at`을 기록해 소프트 삭제하고 사용자 API에서 제외한다. 이동 대상이 먼저 만료돼도 삭제 전까지 알림과 읽음 상태를 유지하며 읽지 않은 알림 수에도 포함한다. 만료 알림을 선택하면 읽음 처리만 하고 대상 화면으로 이동하지 않는다. 상대 시간 문구는 `createdAt`을 기준으로 클라이언트가 계산한다.

## 10. 사용자용 API에서 제외한 내부 작업

다음 작업은 요구사항과 DDL에는 존재하지만 사용자가 직접 호출하는 공개 HTTP API로 만들지 않는다.

| 내부 작업 | 근거 | 향후 계약 |
| --- | --- | --- |
| D-1 날씨 조회 실행 | `weather_checks` | 스케줄러·날씨 제공자 adapter |
| AI 일정 생성 실행·재시도 | `schedule_generation_jobs` | 비동기 작업 큐와 worker |
| 일별 포토 미션 생성 실행 | `mission_generation_jobs` | 비동기 작업 큐와 worker |
| 사진 AI 판정 실행 | `photo_evaluations` | 객체 저장 이벤트 또는 작업 큐와 worker |
| 완료 여행 커뮤니티 게시 | `community_posts` | 여행 종료 이벤트 처리 |
| 날씨·일정 알림 생성 | `notifications` | 내부 도메인 이벤트와 알림 worker |
| 채팅 위반·제재 누적 | `chat_violations`, `chat_sanctions` | 메시지 검사와 제재 정책 처리 |

worker callback을 HTTP로 구현할지는 배포 구조가 정해질 때 별도의 내부 API 계약으로 다룬다.

## 11. 목록 작성 중 확인된 충돌·누락·정책 의존성

### 11.1 남아 있는 후속 결정

- 실시간 채팅의 전송 기술, 연결 경로, 인증 전달 방식과 ACK 형식
- API 응답시간, 채팅 지연시간, 동시 사용자와 가용성의 정량 목표

### 11.2 데이터 모델 또는 외부 연동 확인이 필요한 부분

- `users.username VARCHAR(20)`은 최대 20자인 카카오 닉네임의 앞뒤 공백을 제거해 필수로 저장하며 API에서는 null이 아닌 `userName`으로 노출한다. 닉네임이 누락됐거나 공백 제거 후 비어 있으면 OAuth 사용자 정보 조회 실패로 처리한다.
- `schedule_legs.distance_meters`는 인접 장소의 좌표 간 거리를 미터 단위로 저장한다. 이동수단과 예상 소요 시간은 저장하지 않는다. 지도의 동선은 `schedule_stops`의 방문 순서와 `places`의 좌표를 직선으로 연결해 표시한다.
- S3 Presigned URL과 서버가 생성한 임시 `uploadKey`를 통한 이미지 업로드·조회, 만료, 파일 검증, 미확정 객체 정리 및 이미지 후처리 정책이 확정됐다.
- 실시간 채팅 전송 기술, 인증·구독·전송·수신·ACK·재연결 이벤트 계약이 정해지지 않았다.

## 12. 묶음별 상세화 순서

1. 전체 공통 정책
2. 여행
3. 설문 기반 일정 생성
4. 오픈 채팅
5. 커뮤니티
6. 포토 미션
7. 알림

인증, 인가 묶음은 [`API_SPEC.md`](./API_SPEC.md)에서 먼저 승인됐으며 현재 사용자 조회만 사용자 응답 필드 확정이 남아 있다.
