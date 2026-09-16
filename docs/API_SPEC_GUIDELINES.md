# PlanIt API 명세 작성 규약

## 1. 목적과 적용 범위

이 문서는 PlanIt HTTP API 명세를 같은 형식과 기준으로 작성하기 위한 기준 문서다.

- 참고 형식: [개인 프로젝트 API 명세 Google Sheet](https://docs.google.com/spreadsheets/d/18Dn_xC6QP6FoFVNtN852TeK1M9c_ouNdzMJQyY5kmck/edit?gid=1878554884#gid=1878554884)의 `커뮤니티` 탭
- 적용 대상: 신규 API, 변경 API, 비동기 작업 API, 파일 업로드 API
- 관련 근거: [`FIGMA_FINAL_BUSINESS_RULES.md`](../FIGMA_FINAL_BUSINESS_RULES.md), [`PROJECT_OVERVIEW.md`](../PROJECT_OVERVIEW.md), [`erd/planit_consolidated.sql`](../erd/planit_consolidated.sql)
- DDL은 도메인과 필드 파악을 위한 참고 자료다. DDL의 ID 발급 방식, 타입과 제약조건을 별도 승인 없이 API 계약에 그대로 적용하지 않는다.
- 이 문서는 작성 형식과 공통 규약을 정의한다. 실제 엔드포인트의 확정 계약은 별도 API 명세에 기록한다.
- 화면, 요구사항 또는 ERD만으로 확인할 수 없는 동작을 임의로 확정하지 않고 `TBD`로 표시한다.

## 2. 참고 문서 형식 평가 및 채택 결정

### 2.1 유지할 부분

참고 문서의 다음 구성은 요청과 응답을 한 화면에서 빠르게 비교할 수 있어 유지한다.

| 구분 | 유지할 정보 |
| --- | --- |
| API 식별 | API 이름 |
| 요청 | Request method, URL, body, 설명 |
| 응답 | Response status code, response body |
| 예시 | 실제 JSON 형태의 요청·응답 예시 |

### 2.2 보완할 부분

참고 문서의 큰 틀은 사용할 수 있지만 아래 문제 때문에 원본 구조를 그대로 복제하지는 않는다.

1. Path, Query, Header, Cookie, Body가 모두 `body`와 `설명`에 섞여 있어 값의 전달 위치가 불명확하다.
2. GET 요청에 Access Token과 사용자 ID를 body로 보내는 예시가 있다. GET body는 사용하지 않고 인증 정보는 인증 정책에 따른 Header 또는 Cookie로 분리해야 한다.
3. 인증된 사용자의 식별자는 요청 body의 `user_id`를 신뢰하지 않고 인증 주체로부터 결정해야 한다.
4. `203`을 인가 실패로 사용하거나 `204` 응답에 JSON body를 넣는 등 HTTP 상태 코드 의미와 맞지 않는 예시가 있다.
5. `message`가 기계 판독용 코드 역할까지 담당한다. 안정적인 `code`와 사용자 표시용 `message`를 분리해야 한다.
6. 다수의 예시가 쉼표, 따옴표, 배열 표기 누락 등으로 유효한 JSON이 아니다. 모든 예시는 파싱 가능한 JSON이어야 한다.
7. `/list`, `/modify`, `/regist`, `/delete-account`처럼 화면 이동이나 동사를 나타내는 URI가 섞여 있다. API URI는 화면이 아닌 리소스를 표현해야 한다.
8. 필드별 필수 여부, 타입, 제약, 기본값과 예시가 한 문단에 섞여 있다. 이를 표의 별도 열로 분리해야 한다.
9. 성공·실패 조건, 인증·인가, 페이지네이션, 멱등성, 동시성 및 부수 효과가 명시되지 않았다.
10. 모든 API에 `500`, `503`을 일괄 기재하면 실제 복구 가능성과 클라이언트 처리 기준을 알 수 없다. 엔드포인트에서 실제 발생 가능한 오류만 기록해야 한다.

### 2.3 결론

`API 이름 → 요청 → 응답`이라는 상위 구조는 채택한다. 다만 요청 파라미터의 위치를 분리하고, 응답 envelope와 오류 형식을 표준화하며, 인증·페이지네이션·멱등성 등의 계약 정보를 추가한 아래 템플릿을 사용한다.

## 3. 명세 상태와 신뢰 수준

각 API에는 다음 상태 중 하나를 반드시 표시한다.

| 상태 | 의미 |
| --- | --- |
| `DRAFT` | 논의 중이며 변경될 수 있음 |
| `APPROVED` | 팀이 계약을 합의했으나 구현 여부는 확인하지 않음 |
| `IMPLEMENTED` | 해당 계약을 처리하는 코드가 존재함 |
| `VERIFIED` | 계약 또는 통합 테스트로 요청·응답 동작까지 확인함 |

- 코드가 존재한다는 사실만으로 `VERIFIED`를 사용하지 않는다.
- 문서와 구현이 다르면 조용히 한쪽을 맞추지 말고 차이, 영향 범위, 마이그레이션 필요 여부를 먼저 기록한다.
- 변경 이력에는 변경일, 변경자, 변경 이유, 호환성 영향과 관련 요구사항 ID를 남긴다.

## 4. 엔드포인트 작성 템플릿

아래 블록을 API마다 복사해 사용한다.

### `{API 이름}`

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `DRAFT` / `APPROVED` / `IMPLEMENTED` / `VERIFIED` |
| Method | `GET` / `POST` / `PUT` / `PATCH` / `DELETE` |
| URL | `/api/...` |
| 설명 | 이 API가 수행하는 한 가지 책임 |
| 인증 | 불필요 / 필요 / `TBD` |
| 인가 | 호출 가능한 역할 또는 소유권 조건 |
| 멱등성 | 불필요 / `Idempotency-Key` 필요 / 자연 멱등 |
| 관련 규칙 | `BR-...`, 기능 요구사항 ID, ERD 테이블 |

#### 요청

| 위치 | 필드 | 타입 | 필수 | 제약·기본값 | 설명 | 예시 |
| --- | --- | --- | --- | --- | --- | --- |
| Path | `resourceId` | string | Y | `BIGINT AUTO_INCREMENT` ID의 10진수 문자열 | 대상 리소스 ID | `"12345"` |
| Query | `cursor` | string | N | 최초 요청 시 생략 | 다음 페이지 커서 | `"..."` |
| Header | `Idempotency-Key` | string(UUIDv7) | 조건부 | 재시도 가능한 생성 요청 | 중복 처리 방지 키 | `"019..."` |
| Body | `fieldName` | string | Y | 길이·허용값 기재 | 필드 설명 | `"value"` |

요청 예시:

```http
POST /api/resources
Authorization: Bearer {accessToken}
Idempotency-Key: {uuidV7}
Content-Type: application/json
```

```json
{
  "fieldName": "value"
}
```

#### 응답

| HTTP 상태 | API 코드 | 발생 조건 | `data` |
| ---: | --- | --- | --- |
| `201 Created` | `RESOURCE_CREATED` | 리소스 생성 완료 | 생성 결과 |
| `400 Bad Request` | `INVALID_REQUEST` | 형식 또는 기본 입력 검증 실패 | `null` |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | 인증 정보 없음 또는 유효하지 않음 | `null` |
| `403 Forbidden` | `ACCESS_DENIED` | 인증됐지만 해당 작업 권한 없음 | `null` |
| `409 Conflict` | 도메인별 코드 | 현재 리소스 상태와 충돌 | `null` |

성공 응답 예시:

```json
{
  "code": "RESOURCE_CREATED",
  "message": "리소스가 생성되었습니다.",
  "data": {
    "resourceId": "123456789012345678"
  }
}
```

오류 응답 예시:

```json
{
  "code": "INVALID_REQUEST",
  "message": "요청 값을 확인해 주세요.",
  "data": null,
  "errors": [
    {
      "field": "fieldName",
      "reason": "REQUIRED"
    }
  ]
}
```

#### 처리 규칙

- 사전 조건:
- 성공 시 상태 변화:
- 트랜잭션 범위:
- 동시 요청 처리:
- 재시도 동작:
- 외부 시스템 의존성과 실패 처리:
- 로그·메트릭·감사 이벤트:

## 5. 요청 작성 규약

### 5.1 HTTP Method

- `GET`: 조회 전용이며 request body를 사용하지 않는다.
- `POST`: 새 리소스 생성 또는 자연스럽게 리소스로 표현하기 어려운 작업 시작에 사용한다.
- `PUT`: 대상 전체를 교체하고 같은 요청을 반복해도 결과가 같을 때 사용한다.
- `PATCH`: 대상의 일부 필드를 변경할 때 사용한다.
- `DELETE`: 리소스 삭제 또는 취소에 사용하며, 소프트 삭제 여부는 처리 규칙에 명시한다.

### 5.2 URL

- 기본 prefix는 `/api`로 한다.
- 현재 API는 PlanIt 모바일 웹 클라이언트만 사용하는 단일 계약이므로 URL에 `v1` 같은 버전 segment를 두지 않는다.
- 프론트엔드와 백엔드의 호환되지 않는 계약 변경은 함께 배포한다. 향후 외부 클라이언트, 독립 배포 클라이언트 또는 여러 계약의 동시 지원이 필요해질 때 별도의 버전 전략을 도입한다.
- URI는 소문자 복수 명사를 기본으로 하고 단어 구분은 kebab-case를 사용한다.
- 화면 이동을 위한 `/list`, `/modify` 같은 경로를 만들지 않는다.
- 생성·수정·삭제 의미를 URI 동사로 중복 표현하지 않는다.
- 하위 리소스 관계가 계약상 중요할 때만 중첩한다. 예: `/trips/{tripId}/members`.
- 명령형 동사가 불가피한 경우 해당 동작을 작업 리소스로 모델링할 수 있는지 먼저 검토한다.

### 5.3 파라미터 위치

- Path: 특정 리소스를 식별한다.
- Query: 필터, 정렬, 검색과 페이지네이션에 사용한다.
- Header: 인증, 멱등성, 조건부 요청과 추적 정보에 사용한다.
- Cookie: 인증 설계에서 확정한 토큰에만 사용한다.
- Body: 생성 또는 변경하려는 도메인 데이터에 사용한다.
- 동일한 의미의 값을 둘 이상의 위치에 중복 전달하지 않는다.
- 현재 사용자 ID와 역할은 인증 정보로부터 결정하며 body의 사용자 ID로 권한을 판단하지 않는다.

### 5.4 JSON과 데이터 타입

- JSON 필드명은 `camelCase`를 사용한다.
- JSON 문자열, 객체와 배열을 문법에 맞게 표기하고 예시를 파서로 검증한다.
- 이 절의 식별자 정책은 `APPROVED`다.
- 일반 테이블의 내부 PK는 `BIGINT AUTO_INCREMENT`로 생성한다.
- `BIGINT` 리소스 ID는 JavaScript 정수 정밀도 손실을 막기 위해 JSON과 Path에서 10진수 문자열로 전달한다.
- 사용자 외부 식별자와 JWT `sub`는 기존 계약대로 UUIDv7 `publicId`를 사용하고 내부 `users.id`를 노출하지 않는다.
- 멱등성 키와 분산 이벤트 식별자는 UUIDv7을 사용한다.
- 채팅 메시지는 현재 `clientMessageId`와 중복 방지 계약을 사용하지 않는다. 연결 오류 후 클라이언트가 같은 내용을 다시 전송하면 중복 메시지가 저장될 수 있다.
- UUID는 표준 하이픈 문자열로 전달한다. DB의 `BINARY(16)` 저장 형식을 API에 노출하지 않는다.
- 금액이나 정밀한 소수는 부동소수점 오차가 문제가 되면 문자열 또는 단위가 명확한 정수로 정의한다.
- boolean은 문자열 `"true"`가 아니라 JSON boolean `true`를 사용한다.
- 배열은 반드시 `[]`, 객체는 `{}`로 표기한다.

### 5.5 날짜와 시간

- 이 절의 날짜·시각 정책은 `APPROVED`다.
- 날짜는 ISO 8601의 `YYYY-MM-DD`를 사용하고 `Asia/Seoul`의 달력 날짜로 해석한다.
- DB의 도메인 시각은 저장 시점부터 `Asia/Seoul` 기준 현지 시각으로 저장한다.
- API의 시각은 ISO 8601 형식과 서울 시각의 UTC offset을 함께 사용한다. 예: `2026-09-06T14:30:00.123+09:00`.
- 애플리케이션과 DB 연결은 시스템 기본 시간대에 의존하지 않고 `Asia/Seoul`을 명시적으로 사용한다.
- 외부 시스템에서 UTC 시각을 받으면 도메인 DB에 저장하기 전에 서울 시각으로 변환한다.
- JWT `iat`·`exp` 같은 표준 NumericDate와 Presigned URL 만료 계산은 절대 시각이므로 Unix epoch 의미를 유지한다.

### 5.6 선택 필드, `null`, 빈 값

- 값이 존재하지 않는 것과 필드를 변경하지 않는 것을 구분해야 한다.
- `PATCH`에서 생략은 변경하지 않음을 뜻한다. `null` 허용 여부와 의미는 필드별로 명시한다.
- 빈 문자열, 공백 문자열, 빈 배열의 허용 여부를 검증 조건에 적는다.
- 서버 기본값이 있더라도 클라이언트 계약에 영향을 주면 명세에 기록한다.

### 5.7 인증 정보

- Access Token과 Refresh Token을 query 또는 일반 request body에 넣지 않는다.
- Access Token은 클라이언트 메모리에만 저장하고 `Authorization: Bearer {accessToken}` Header로 전달한다.
- Access Token 만료 시간은 발급 후 15분이다.
- Access Token은 최소 2048비트 RSA 키와 `RS256`으로 서명한 JWT다.
- JWT Header는 `alg=RS256`, `typ=at+jwt`, `kid={keyId}`를 포함한다. 검증 서버는 Header가 지정한 임의 알고리즘을 선택하지 않고 `RS256`만 허용한다.
- Access Token의 필수 claim은 `iss=planit-auth`, `aud=planit-api`, `sub=users.public_id`, `iat`, `exp`, `jti`다. `sub`는 UUIDv7 문자열로 전달한다.
- JWT 시간 claim 검증에 적용하는 허용 시간 오차는 최대 60초다.
- Private Key는 인증 서버만 보유하고 Secret Manager에 저장한다. API 서버는 `/.well-known/jwks.json`에서 제공되는 Public Key로 검증한다.
- JWKS는 표준 JWK Set 구조와 `Content-Type: application/json`, `Cache-Control: public, max-age=300`을 사용하며 공통 API envelope를 적용하지 않는다.
- 정상 키 교체 시 새 Public Key를 먼저 게시하고 5분 후 새 키로 발급을 시작하며, 기존 Public Key는 최소 1시간 유지한다.
- 캐시에 없는 `kid`를 받으면 JWKS를 즉시 한 번 갱신하고, 갱신 후에도 대응하는 키가 없으면 `401 Unauthorized`로 거부한다.
- JWT 서명 키는 90일마다 교체하며 유출 의심 시 즉시 비상 교체한다. 비상 교체의 캐시 무효화와 전파 방식은 현재 범위에서 별도로 확정하지 않는다.
- Refresh Token은 이름이 `refresh_token`이고 `Max-Age=2592000`, `Path=/api/auth`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Domain` 미지정인 Cookie로만 전달한다.
- 운영·스테이징에서는 `Secure`를 반드시 활성화하고 로컬 HTTP 개발 환경에서만 분리된 설정으로 비활성화할 수 있다.
- frontend와 API가 cross-site로 배포되어 `SameSite=None`이 필요해지면 `Secure`와 CSRF·CORS 정책을 함께 재검토한 후 계약을 변경한다.
- Cookie 기반 Refresh·Logout 요청은 `Origin`의 scheme, host, port 전체를 환경별 허용 frontend origin과 정확히 비교하고, 값이 없거나 허용되지 않으면 `403 Forbidden`으로 거부한다.
- cross-origin Cookie 요청은 클라이언트가 credential을 포함하고 서버가 정확한 frontend origin과 `Access-Control-Allow-Credentials: true`를 반환한다. credential 허용과 `Access-Control-Allow-Origin: *`를 함께 사용하지 않는다.
- Refresh·Logout의 `Origin` 검증 실패 API 코드는 `ORIGIN_NOT_ALLOWED`다.
- Refresh Token은 갱신할 때 회전하지 않으며 최초 발급 시각을 기준으로 한 30일 만료 시각도 연장하지 않는다.
- Refresh 성공 응답은 `Cache-Control: no-store`와 `Pragma: no-cache`를 사용하고 Access Token, `Bearer` token type, 900초 만료 시간을 반환한다.
- Refresh Token Cookie가 없으면 `REFRESH_TOKEN_REQUIRED`, 존재하지만 DB 행·만료·사용자 상태 때문에 사용할 수 없으면 `REFRESH_TOKEN_INVALID`를 반환한다. 탈퇴 여부를 별도 인증 오류로 노출하지 않는다.
- Refresh 요청은 호출마다 서로 다른 `jti`의 Access Token을 발급할 수 있으므로 멱등성을 보장하지 않는다.
- `refresh_tokens.revoked_at`이 `NULL`이고 `expires_at`이 지나지 않은 토큰만 활성으로 취급한다.
- 로그아웃은 현재 Cookie에 대응하는 Refresh Token의 `revoked_at`을 기록하고 Cookie를 제거한다.
- 로그아웃은 Cookie가 없거나 토큰이 이미 폐기·만료·미등록이어도 `204 No Content`로 처리하는 자연 멱등 API다.
- 로그아웃의 DB 조회 또는 폐기 기록이 실패하면 `503 LOGOUT_UNAVAILABLE`을 반환하고 재시도를 위해 Cookie를 제거하지 않는다.
- 로그아웃 성공 시 DB 처리가 끝난 뒤 동일한 Cookie 이름, `Path`, `Domain` 범위와 `Max-Age=0`으로 Cookie를 제거한다.
- 회원 탈퇴는 해당 사용자의 모든 미폐기 Refresh Token에 `revoked_at`을 기록하고 Cookie를 제거한다.
- 회원 탈퇴는 Access Token으로 현재 사용자를 식별하고 별도 재인증 없이 카카오 연결 해제를 먼저 동기 수행한다.
- 카카오 연결 해제 실패는 `503 KAKAO_UNLINK_UNAVAILABLE`이며 로컬 데이터를 변경하지 않는다.
- 카카오 연결 해제 후 사용자 소프트 삭제, 모든 활성 Refresh Token 폐기와 OAuth 정보 삭제를 하나의 로컬 트랜잭션으로 처리한다.
- 로컬 탈퇴 트랜잭션 실패는 `503 WITHDRAWAL_UNAVAILABLE`이며 전체 변경을 롤백하고 재시도를 위해 Cookie를 제거하지 않는다.
- 로컬 OAuth 정보가 남은 탈퇴 재시도에서 카카오 미연결 오류 코드 `-101`만 이미 연결 해제된 상태로 처리한다.
- 만료·폐기된 Refresh Token 행은 인증 처리 과정에서 물리 삭제하지 않고 이력으로 유지한다.
- 만료·폐기 이력의 보관 기간, 정기 물리 삭제와 관련 인덱스 설계는 초기 구현 범위에서 제외하고 운영 단계에서 별도로 결정한다.
- 토큰 원문, OAuth 자격 증명과 외부 API 키는 예시·로그·응답에 기록하지 않는다.
- 명세 예시에는 실제 토큰 대신 `{accessToken}` 같은 명확한 placeholder를 사용한다.
- 자세한 발급·갱신·폐기 계약은 [`API_SPEC.md`](./API_SPEC.md)의 인증 영역을 따른다.

### 5.8 S3 파일 업로드와 조회

- 프로필 이미지, 채팅 이미지와 포토 미션 사진의 원본 파일은 S3에 저장하고 객체 키와 파일 메타데이터는 `image_files`에서 관리한다. 사용자·채팅 메시지·미션 사진은 `image_file_id`로 파일을 연결한다.
- `image_files.image_purpose`는 `USER_PROFILE`, `CHAT_MESSAGE`, `MISSION_PHOTO`를 사용하며 JPA에서는 문자열 Enum으로 매핑한다.
- 업로드가 검증된 뒤에만 `image_files` 행을 생성하므로 `image_key`, `mime_type`, `size_bytes`는 필수값이다. 썸네일이 아직 생성되지 않았거나 외부 이미지에 원본 파일명이 없는 경우를 허용하기 위해 `thumbnail_key`, `original_filename`은 선택값으로 둔다.
- 업로드와 조회에는 각각 용도와 HTTP Method가 제한된 S3 Presigned URL을 사용한다. 버킷과 객체를 public으로 공개하지 않는다.
- 업로드는 다음 5단계로 처리한다.
  1. 클라이언트가 파일명, MIME type과 크기를 API에 전달해 업로드 요청을 생성한다.
  2. 서버가 인증·인가와 파일 조건을 검증하고 현재 사용자와 이미지 용도에 맞는 임시 `uploadKey` 및 업로드용 Presigned URL을 반환한다.
  3. 클라이언트가 Presigned URL로 S3에 직접 업로드한다.
  4. 클라이언트가 도메인 제출 API 또는 채팅 전송 이벤트에 `uploadKey`를 전달한다.
  5. 서버가 로그인 사용자와 `uploadKey`의 소유 경로·용도를 확인하고 객체 존재 여부·메타데이터를 검증한 뒤 `image_files` 행을 생성하여 도메인 데이터에 `image_file_id`로 연결한다.
- `uploadKey`는 서버가 생성하며 클라이언트가 임의로 변경한 키나 다른 사용자의 객체 키를 도메인 데이터에 연결하도록 허용하지 않는다.
- 카카오 프로필처럼 백엔드가 외부 이미지를 직접 가져와 S3에 저장하는 경우에는 클라이언트 업로드 절차 없이 `image_files`를 바로 생성한다.
- 조회 API는 권한 확인 후 조회용 Presigned URL을 응답한다. DB의 객체 키, 버킷 이름과 내부 S3 경로는 외부 계약으로 노출하지 않는다.
- Presigned URL은 만료 전까지만 유효하므로 영구 저장하지 않고 필요할 때 다시 발급받는다.
- 업로드용 Presigned URL의 유효시간은 10분, 조회용 Presigned URL의 유효시간은 5분으로 한다.
- 업로드 URL이 만료되면 기존 값을 연장하거나 다시 활성화하지 않고 새로운 업로드 요청을 통해 URL과 `uploadKey`를 다시 발급한다.
- 허용 이미지 형식은 JPEG(`image/jpeg`), PNG(`image/png`), WebP(`image/webp`)로 제한한다. SVG, GIF, HEIC/HEIF를 포함한 다른 형식은 허용하지 않는다.
- 용도별 원본 파일 최대 크기는 `USER_PROFILE` 5 MB, `CHAT_MESSAGE` 5 MB, `MISSION_PHOTO` 15 MB로 제한한다. 여기서 1 MB는 1,048,576 byte로 계산한다.
- 서버는 클라이언트가 제출한 파일명·확장자·MIME type만 신뢰하지 않는다. 업로드 완료 후 S3 객체의 실제 크기와 파일 시그니처를 검사하고, 요청 당시의 MIME type·크기 조건과 일치할 때만 `image_files` 행을 생성한다.
- 파일 형식 또는 크기 검증에 실패하면 도메인 데이터에 연결하지 않고 요청을 거부한다.
- Presigned URL로 업로드하는 객체는 먼저 영구 이미지와 분리된 S3 임시 영역에 저장한다. 서버가 발급한 `uploadKey`가 제출되고 파일 검증이 성공한 경우에만 영구 이미지로 확정하고 `image_files` 행과 도메인 연결을 생성한다.
- 제출되지 않은 임시 객체는 업로드 후 24시간이 지나면 S3 Lifecycle 삭제 대상으로 처리한다. Lifecycle 삭제는 비동기로 실행되므로 정확히 24시간이 되는 시점의 즉시 삭제를 보장하지 않는다.
- 미확정 임시 객체 정리를 위한 별도의 애플리케이션 스케줄러는 두지 않는다.
- 업로드된 원본 객체는 임시 객체로만 취급한다. 서버는 EXIF 방향 정보를 반영해 이미지 방향을 정상화하고 GPS를 포함한 EXIF 메타데이터를 모두 제거한 뒤, 정규화된 이미지를 영구 객체로 저장한다.
- `image_files.image_key`는 정규화된 영구 이미지 객체 키를 저장한다. 정규화가 실패하면 이미지 제출 전체를 실패 처리하며 `image_files` 행과 도메인 연결을 생성하지 않는다.
- 목록 화면용 썸네일은 원본 비율을 유지하면서 최대 512×512 크기의 WebP로 비동기 생성한다. 작은 이미지는 확대하지 않고 강제로 정사각형으로 자르지 않는다.
- 썸네일 생성 전까지 `thumbnail_key`는 `NULL`이며, 썸네일 생성 실패는 정규화된 이미지 제출 결과를 취소하지 않는다.
- 프로필 이미지의 원형 표시는 저장된 파일을 자르지 않고 클라이언트 화면에서 처리한다.
- 정규화된 영구 이미지 저장이 완료되면 업로드에 사용한 임시 원본 객체를 삭제한다.

## 6. 응답 작성 규약

### 6.1 공통 envelope

이 절의 응답 구조는 `APPROVED`다. 요청 추적용 식별자는 현재 공통 응답에 포함하지 않는다.

body가 있는 JSON 응답은 다음 필드를 기본으로 사용한다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `code` | string | Y | 클라이언트가 분기할 수 있는 안정적인 API 코드 |
| `message` | string | Y | 사용자 또는 개발자가 이해할 수 있는 설명 |
| `data` | object, array, null | Y | 성공 결과. 결과가 없거나 오류이면 `null` |
| `errors` | array | N | 필드 단위 검증 오류 등의 구조화된 상세 정보 |

- HTTP 상태는 전송 결과를, `code`는 구체적인 비즈니스 결과를 표현한다.
- 클라이언트 로직은 변경될 수 있는 `message` 문구가 아니라 `code`를 기준으로 분기한다.
- `message` 정책은 `APPROVED`다. 서버는 모든 JSON 응답에 안전한 기본 한국어 설명을 제공한다.
- 토스트·팝업 등 실제 사용자 화면 문구와 다국어 번역은 frontend가 `code`를 기준으로 관리한다.
- 서버는 현재 `Accept-Language`에 따른 응답 메시지 번역을 제공하지 않는다.
- 내부 예외명, SQL, 스택 트레이스, 외부 API의 민감한 원문 오류를 응답하지 않는다.
- 사용자에게 구분할 필요가 없는 AI·외부 API 내부 실패는 안전한 공통 코드로 응답하고 상세 원인은 로그에 남긴다.

### 6.2 body가 없는 응답

- `204 No Content`는 response body를 절대 포함하지 않는다.
- 성공 메시지 또는 결과 데이터가 필요하면 `200 OK`나 `201 Created`와 JSON body를 사용한다.

### 6.3 주요 HTTP 상태 코드

| 상태 | 사용 기준 |
| ---: | --- |
| `200 OK` | 조회·수정·삭제 성공이며 body가 있음 |
| `201 Created` | 동기 리소스 생성 완료. 생성 결과 또는 `Location`을 제공 |
| `202 Accepted` | 비동기 작업을 접수함. 작업 ID와 상태 조회 방법을 제공 |
| `204 No Content` | 성공했고 반환할 body가 없음 |
| `400 Bad Request` | JSON 형식, 타입 또는 일반 입력 검증 실패 |
| `401 Unauthorized` | 인증 정보가 없거나 유효하지 않음 |
| `403 Forbidden` | 인증됐으나 권한·소유권 조건을 충족하지 못함 |
| `404 Not Found` | 대상이 없거나 보안상 존재를 공개하지 않음 |
| `405 Method Not Allowed` | 해당 리소스에서 지원하지 않는 HTTP Method를 호출함 |
| `409 Conflict` | 중복, 정원 초과, 상태 전이 또는 동시성 충돌 |
| `410 Gone` | 과거에는 유효했지만 삭제되어 더 이상 사용할 수 없는 리소스 |
| `413 Payload Too Large` | 업로드 크기 제한 초과 |
| `415 Unsupported Media Type` | 지원하지 않는 Content-Type 또는 파일 형식 |
| `429 Too Many Requests` | 호출 빈도 제한 초과 |
| `500 Internal Server Error` | 예상하지 못한 서버 내부 오류 |
| `503 Service Unavailable` | 일시적으로 처리할 수 없어 재시도가 가능함 |

- 엔드포인트마다 실제로 발생할 수 있고 클라이언트가 구분해야 하는 상태만 기재한다.
- 같은 조건에는 모든 API에서 같은 상태와 공통 코드를 사용한다.

## 7. 페이지네이션 규약

- 이 절의 페이지네이션 정책은 `APPROVED`다.
- 승인된 목록 API는 cursor 기반 페이지네이션을 사용하고 API별 고정 조회 개수를 적용한다. 클라이언트가 임의의 `size`를 지정하지 않는다.
- 정렬 기준과 동률 해소 키를 명세한다. 예: `createdAt DESC, resourceId DESC`.
- cursor는 서버 구현 세부 정보가 드러나지 않는 불투명 문자열로 취급한다.
- 응답의 `items`는 항상 배열이다.

| 목록 | 최초 조회 | 추가 조회 |
| --- | ---: | ---: |
| 메인 페이지 여행방 | 10개 | 10개 |
| 장소 검색 | 10개 | 10개 |
| 채팅 메시지 이력 | 20개 | 20개 |
| 커뮤니티 일정 | 10개 | 10개 |
| 포토 미션 지난 여행 | 10개 | 10개 |
| 알림 | 20개 | 10개 |
| 선택한 여행의 미션 앨범 사진 | 20개 | 20개 |

```json
{
  "code": "RESOURCES_RETRIEVED",
  "message": "목록을 조회했습니다.",
  "data": {
    "items": [],
    "page": {
      "nextCursor": null,
      "hasNext": false
    }
  }
}
```

- 전체 개수가 실제 화면에 필요하고 계산 비용을 감당할 수 있을 때만 `totalCount`를 제공한다.
- 첫 요청은 cursor를 생략하고 추가 요청은 직전 응답의 `nextCursor`를 그대로 전달한다.
- cursor가 잘못됐거나 현재 필터·정렬 조건에 사용할 수 없으면 `400 INVALID_CURSOR`를 반환한다.
- 필터나 정렬을 바꾸면 기존 cursor를 폐기하고 첫 페이지부터 다시 요청한다.
- 위 표에 없는 목록은 전체 데이터를 즉시 로드한다. 데이터 상한이 필요한 목록은 각 도메인 계약에서 최대 개수를 별도로 정한다.

## 8. 멱등성·동시성·비동기 작업

### 8.1 Idempotency Key

- 이 절의 멱등성 정책은 `APPROVED`다.
- 네트워크 재시도로 중복 생성될 수 있는 요청은 UUIDv7 형식의 `Idempotency-Key` Header를 사용한다.
- 적용 범위는 인증 사용자, HTTP Method, 정규화된 API 경로와 Key의 조합이다.
- 클라이언트는 한 번의 사용자 동작에 새 Key를 생성하고 같은 동작의 네트워크 재시도에는 기존 Key를 재사용한다.
- 서버는 검증된 요청 DTO를 정규화하고 SHA-256 해시를 생성해 요청 동일성을 판단한다. Access Token과 Cookie는 해시 대상에서 제외한다.
- Key와 처리 결과는 최초 요청 후 24시간 보관한다.
- 같은 Key와 같은 요청의 재전송은 새 데이터를 만들지 않고 기존 성공 응답 또는 기존 비동기 작업 정보를 반환한다.
- 같은 Key를 다른 요청에 재사용하면 `409 Conflict`와 `IDEMPOTENCY_KEY_REUSED`를 반환한다.
- 동일한 동기 요청이 아직 처리 중이면 `409 Conflict`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`와 `Retry-After: 1`을 반환한다.
- 여행방 생성, AI 일정 생성 시작, 일정 장소 추가, 미션 생성 시작, 사진 제출·재판정과 파일 업로드 요청을 우선 적용 대상으로 한다.
- 일반 `GET`, 자연 멱등인 `PUT`·`DELETE`, 좋아요·조회 기록, Access Token 갱신과 OAuth callback에는 적용하지 않는다.
- 최종 적용 여부는 각 도메인 API 명세에 표시한다.

### 8.2 동시성

- 이 절의 동시성 API 계약은 `APPROVED`다.
- 동시 요청에서도 정원, 단일 방장, 사용자별 단일 참여와 단일 활성 작업 같은 도메인 불변식을 지켜야 한다.
- 하나의 상태 변경 요청은 전부 성공하거나 전부 실패하도록 원자적으로 처리한다.
- 현재 상태와 충돌하면 `409 Conflict`와 도메인별 API 코드를 반환한다.
- 충돌 후 클라이언트가 최신 상태를 다시 조회해야 한다면 해당 API 처리 규칙에 명시한다.
- 화면에서 버튼을 비활성화하는 것만으로 서버 중복 처리를 막았다고 간주하지 않는다.
- DB 잠금, 조건부 갱신, 낙관적 잠금, DB 제약과 분산 잠금은 API 계약에 포함하지 않고 구현 설계에서 선택한다.
- 요청·응답에 `version`을 노출할지는 실제 동시 수정 가능성을 검토해 도메인 묶음별로 결정한다.

### 8.3 비동기 작업

- 이 절의 비동기 작업 API 계약은 `APPROVED`다.
- AI 일정 생성, 포토 미션 생성과 사진 AI 판정·재판정에 비동기 작업 계약을 적용한다.
- 작업을 정상 접수하면 `202 Accepted`, `jobId`, 초기 `status`, 상태 조회 URL과 `Retry-After: 2`를 반환한다.
- 공통 상태는 `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`이며 종료 상태인 `SUCCEEDED`와 `FAILED`는 다른 상태로 되돌리지 않는다.
- 상태 조회가 정상 처리되면 작업의 성공·실패 여부와 관계없이 `200 OK`를 반환하고 실제 결과는 `data.status`로 표현한다.
- 진행 중인 상태 조회 응답은 `Retry-After`를 제공하며 클라이언트는 해당 간격에 따라 polling한다.
- 성공 상태는 결과 리소스 또는 결과 조회 URL을 제공하고, 실패 상태는 안전한 `errorCode`와 재시도 가능 여부를 제공한다.
- 같은 멱등성 키로 작업 시작을 재요청하면 새 작업을 만들지 않고 기존 `jobId`와 상태를 반환한다.
- 다른 멱등성 키로 동일 대상의 활성 작업을 중복 시작하면 `409 Conflict`와 도메인별 `..._ALREADY_IN_PROGRESS` 코드를 반환한다.
- 새로고침이나 재접속 후에도 같은 `jobId`로 진행 상태를 조회할 수 있어야 한다.
- 자동·수동 재시도 횟수와 취소 기능은 도메인별로 결정한다.
- 사용자에게 부여된 횟수 한도는 동작이 정상 완료되어 결과가 생성된 경우에만 차감한다. 네트워크 오류, 외부 시스템 장애와 서버 오류처럼 결과를 만들지 못한 실패는 차감하지 않는다.
- 정상 완료된 AI 판정 결과가 사용자의 기대와 다른 경우는 동작 실패가 아니므로 해당 판정 횟수에 포함한다.
- 서버의 무한 재시도를 막는 내부 자동 실행 횟수는 사용자 횟수 한도와 별개이며 실패한 실행도 기록한다.
- polling을 기본 계약으로 사용하고 WebSocket 진행 알림은 필수로 하지 않는다.
- 작업 큐, worker와 메시지 브로커 구성은 API 계약에 포함하지 않는다.

## 9. 파일 업로드 규약

- 업로드 방식이 multipart인지 presigned URL 기반 직접 업로드인지 명시한다.
- 허용 MIME type, 확장자, 최대 크기, 최대 개수와 이미지 해상도 제한을 적는다.
- 업로드 준비, 실제 업로드, 완료 확정의 각 단계와 만료 시간을 구분한다.
- 객체 저장소 key와 내부 저장 경로를 외부 응답에 직접 노출하지 않는다.
- 포토 미션과 채팅 이미지처럼 정책이 다른 파일은 하나의 공통 제한으로 뭉개지 않는다.

## 10. API 코드 규약

- `code`는 대문자 snake case를 사용한다. 예: `TRIP_CAPACITY_EXCEEDED`.
- 성공 코드는 `도메인 + 결과`, 오류 코드는 `도메인 + 원인`을 기본으로 한다.
- 같은 의미의 공통 오류는 `INVALID_REQUEST`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`처럼 재사용한다.
- 하나의 `INVALID_REQUEST`로 모든 4xx 상황을 표현하지 않는다.
- API 코드 목록에는 HTTP 상태, 발생 조건, 사용자 메시지 노출 정책과 재시도 가능 여부를 함께 관리한다.

### 10.1 공통 오류 코드

| HTTP 상태 | `code` | 발생 조건 |
| ---: | --- | --- |
| `400 Bad Request` | `INVALID_REQUEST` | JSON 형식, 필드 타입 또는 기본 입력 검증 실패 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token 누락, 만료 또는 위조 |
| `403 Forbidden` | `ACCESS_DENIED` | 인증됐지만 해당 작업의 권한 또는 소유권이 없음 |
| `404 Not Found` | `RESOURCE_NOT_FOUND` | 대상이 없거나 보안상 존재를 공개하지 않음 |
| `405 Method Not Allowed` | `METHOD_NOT_ALLOWED` | 해당 리소스가 지원하지 않는 HTTP Method 호출 |
| `413 Payload Too Large` | `FILE_TOO_LARGE` | 허용된 파일 크기 초과 |
| `415 Unsupported Media Type` | `UNSUPPORTED_MEDIA_TYPE` | 허용되지 않은 요청 Content-Type 또는 이미지 형식 |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | 호출 빈도 제한 초과 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 클라이언트에 상세 원인을 공개하지 않는 서버 내부 오류 |
| `503 Service Unavailable` | `SERVICE_UNAVAILABLE` | 외부 서비스 장애 등을 포함한 일시적 처리 불가 |

- 필드별 검증 실패는 `INVALID_REQUEST`와 `errors` 배열을 함께 사용한다.
- 여행 정원 초과나 설문 마감처럼 클라이언트가 별도로 처리해야 하는 비즈니스 오류 코드는 각 도메인 API 묶음에서 정의한다.

## 11. 작성 및 검토 체크리스트

### 요청

- [ ] Method와 URL이 화면 동작이 아니라 리소스 의미를 표현한다.
- [ ] Path, Query, Header, Cookie, Body가 분리되어 있다.
- [ ] 모든 필드에 타입, 필수 여부, 제약과 예시가 있다.
- [ ] 인증 사용자의 ID를 body에서 신뢰하지 않는다.
- [ ] GET 요청에 body가 없다.
- [ ] 예시 JSON이 실제로 파싱된다.

### 응답

- [ ] 성공·실패 조건별 상태 코드와 API 코드가 있다.
- [ ] `204` 응답에 body가 없다.
- [ ] 배열·객체·숫자·boolean 타입이 예시와 일치한다.
- [ ] 리소스 ID가 승인된 생성 방식과 JSON 표현을 따른다.
- [ ] 오류 응답이 내부 정보나 비밀값을 노출하지 않는다.

### 도메인과 운영

- [ ] 인증과 인가 조건이 구분되어 있다.
- [ ] 관련 비즈니스 규칙 ID와 ERD 테이블이 연결되어 있다.
- [ ] 상태 변화와 트랜잭션 범위가 설명되어 있다.
- [ ] 중복 요청과 동시 요청의 결과가 정의되어 있다.
- [ ] 목록 API의 정렬과 페이지네이션이 정의되어 있다.
- [ ] 외부 API 실패와 재시도 가능 여부가 정의되어 있다.
- [ ] 명세 상태가 실제 구현·검증 수준과 일치한다.

## 12. 공통 정책 승인 상태

개별 API 작성 전에 필요한 공통 정책은 승인됐다. 성공 코드와 도메인별 비즈니스 오류 코드는 각 API 묶음을 상세화하면서 누적한다.
