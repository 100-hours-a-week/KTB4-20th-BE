# PlanIt API 명세

## 1. 문서 상태

| 항목 | 내용 |
| --- | --- |
| 현재 작성 범위 | 인증·토큰 공통 계약, 카카오 OAuth 로그인 흐름 |
| 공통 규약 | [`API_SPEC_GUIDELINES.md`](./API_SPEC_GUIDELINES.md) |
| 전체 API 목록 | [`API_INVENTORY.md`](./API_INVENTORY.md) |
| 요구사항 근거 | [`FIGMA_FINAL_BUSINESS_RULES.md`](../FIGMA_FINAL_BUSINESS_RULES.md)의 인증·보안 규칙 |
| 데이터 모델 참고 | [`erd/planit_consolidated.sql`](../erd/planit_consolidated.sql)의 `users`, `oauth_accounts`, `refresh_tokens` |

이 문서는 승인된 계약과 설계 초안을 구분한다. `APPROVED`는 합의된 계약이며, 코드와 테스트가 생기기 전에는 `IMPLEMENTED` 또는 `VERIFIED`로 표시하지 않는다.
DDL의 타입과 제약조건은 참고 자료이며, 이 문서에서 별도로 승인하지 않은 내용을 API 계약으로 자동 적용하지 않는다.

## 2. 인증·토큰 공통 계약

### 2.1 승인된 정책

| 항목 | 계약 | 상태 |
| --- | --- | --- |
| 로그인 제공자 | 카카오 OAuth | `APPROVED` |
| Access Token 보관 | 클라이언트 JavaScript 메모리만 사용 | `APPROVED` |
| Access Token 전달 | `Authorization: Bearer {accessToken}` | `APPROVED` |
| Access Token 수명 | 발급 후 15분, 900초 | `APPROVED` |
| Access Token 형식 | `RS256`으로 서명한 JWT, RSA 키 최소 2048비트 | `APPROVED` |
| Access Token 식별 | Header의 `typ=at+jwt`, `kid={keyId}` 사용 | `APPROVED` |
| Access Token 필수 claim | `iss`, `aud`, `sub`, `iat`, `exp`, `jti` | `APPROVED` |
| JWT 허용 시간 오차 | 최대 60초 | `APPROVED` |
| JWT 키 보관 | Private Key는 인증 서버의 Secret Manager에 보관, Public Key는 검증 서버에 배포 | `APPROVED` |
| JWT 키 교체 | 정기 90일, 교체된 기존 Public Key 최소 1시간 유지, 유출 의심 시 즉시 비상 교체 | `APPROVED` |
| Public Key 제공 | `/.well-known/jwks.json` | `APPROVED` |
| Refresh Token 보관 | 브라우저의 `Secure`, `HttpOnly` Cookie | `APPROVED` |
| Refresh Token 수명 | 발급 후 30일, 절대 만료 | `APPROVED` |
| Refresh Token Cookie | `refresh_token`, `Max-Age=2592000`, `Path=/api/auth`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Domain` 미지정 | `APPROVED` |
| Cookie 요청 출처 검증 | Refresh·Logout 요청의 `Origin`을 허용된 frontend origin과 정확히 비교 | `APPROVED` |
| Cookie 요청 CORS | credential 허용 시 정확한 origin만 허용하고 wildcard를 사용하지 않음 | `APPROVED` |
| Refresh Token 회전 | 사용하지 않음 | `APPROVED` |
| Refresh Token 재사용 탐지 | 사용하지 않음 | `APPROVED` |
| Refresh Token 폐기 | `revoked_at`이 `NULL`인 토큰만 활성으로 보고 명시적 폐기 시 시각 기록 | `APPROVED` |
| 로그아웃 | 현재 Cookie의 Refresh Token에 `revoked_at` 기록 및 Cookie 제거 | `APPROVED` |
| 회원 탈퇴 | 사용자의 모든 활성 Refresh Token에 `revoked_at` 기록 및 Cookie 제거 | `APPROVED` |
| 회원 탈퇴 재인증 | 별도 재인증 또는 확인용 credential을 요구하지 않음 | `APPROVED` |
| 로그아웃 시 Access Token 즉시 차단 | 사용하지 않음. 남은 최대 15분의 유효 시간을 수용 | `APPROVED` |
| 회원 탈퇴 시 카카오 연결 해제 | 로컬 정보 삭제 전에 동기 호출하며 성공 또는 이미 해제된 상태여야 함 | `APPROVED` |
| 회원 탈퇴 시 소셜 로그인 정보 | 카카오 연결 해제 성공 후 `oauth_accounts` 행 삭제 | `APPROVED` |
| 모바일 웹 카카오 로그인 방식 | 팝업 없이 현재 화면 전체를 카카오 로그인으로 리다이렉트 | `APPROVED` |
| 카카오 callback | PlanIt 백엔드의 `/api/auth/kakao/callback`에서 처리 | `APPROVED` |
| 로그인 후 복귀 | 동일 frontend origin의 상대 경로 `returnTo` allowlist 사용 | `APPROVED` |
| 환경별 URL | 설정값으로 분리하며 현재 localhost, 운영 도메인은 확정 전까지 `TBD` | `APPROVED` |

Access Token 15분은 메모리 보관과 자동 갱신을 전제로 사용성과 탈취 노출 시간을 절충한 프로젝트 기본값이다. Refresh Token의 30일 만료는 갱신 요청으로 연장하지 않는다.

### 2.2 토큰 표현과 저장

이 절에서 Access Token의 JWT 표현과 키 운영 방식은 `APPROVED`다. Refresh Token의 opaque token 세부 표현은 별도 상태로 관리하며, 저장 위치, 만료 시간, 비회전 및 폐기 범위는 2.1의 승인된 정책을 따른다.

#### Access Token (`APPROVED`)

- `RS256`으로 서명한 JWT를 사용하며 RSA 키는 최소 2048비트다.
- Header는 `alg=RS256`, `typ=at+jwt`, `kid={keyId}`를 포함한다.
- 검증 서버는 토큰 Header의 `alg`를 보고 검증 알고리즘을 선택하지 않는다. 허용 알고리즘을 `RS256`으로 고정하고 다른 값은 거부한다.
- `typ=at+jwt`는 다른 종류의 JWT와 Access Token을 구분한다.
- `kid`는 키 교체 중 함께 존재하는 현재 키와 기존 키 중 검증할 Public Key를 식별한다.
- 필수 claim은 `iss`, `aud`, `sub`, `iat`, `exp`, `jti`다.
- `iss`의 고정 논리 식별자는 `planit-auth`, `aud`의 고정 논리 식별자는 `planit-api`를 사용한다. 배포 URL이나 환경에 따라 값이 바뀌지 않는다.
- `sub`에는 `users.public_id`인 UUIDv7 문자열을 사용하고 내부 DB PK를 직접 노출하지 않는다.
- `iat`는 발급 시각, `exp`는 발급 시각으로부터 15분 후다.
- `jti`는 토큰마다 새로 생성하는 고유 ID이며 로그 추적과 향후 차단·재사용 분석의 확장 지점으로 사용한다. 현재 `jti` denylist는 운영하지 않는다.
- 검증 서버는 `alg`, 서명, `typ`, `kid`, `iss`, `aud`, `sub`, `iat`, `exp`를 검증하고 시간 claim에는 최대 60초의 허용 오차만 적용한다.
- 클라이언트는 Access Token을 `localStorage`, `sessionStorage`, IndexedDB 또는 일반 Cookie에 저장하지 않는다.
- 페이지 새로고침으로 메모리가 초기화되면 Refresh API를 한 번 호출하여 새 Access Token을 받는다.

#### JWT 키 운영과 JWKS (`APPROVED`)

- Private Key는 인증 서버만 보유하며 Secret Manager에 저장한다. 트래픽과 보안·운영 요구가 커지면 KMS 기반 서명을 별도 변경 단위로 검토한다.
- API 서버는 Public Key만 사용해 Access Token을 검증한다.
- 키는 90일마다 정기 교체한다. 새 Private Key로 발급을 시작한 뒤 기존 Public Key는 Access Token 수명, 허용 시간 오차, 배포 지연과 Public Key 캐시를 고려해 최소 1시간 유지한다.
- 유출이 의심되면 정기 주기와 관계없이 즉시 비상 교체한다. 비상 교체의 캐시 무효화와 전파 방식은 현재 범위에서 별도로 확정하지 않는다.
- 인증 서버는 인증 없이 조회할 수 있는 `GET /.well-known/jwks.json`으로 현재 Public Key와 정상 교체 유예 중인 기존 Public Key를 표준 JWK Set 형식으로 제공한다. Private Key 정보는 절대 포함하지 않는다.
- 각 JWK는 RSA 검증에 필요한 `kty`, `use`, `alg`, `kid`, `n`, `e`를 포함하며 JWT Header의 `kid`와 대응한다.
- JWKS 응답은 `Content-Type: application/json`과 `Cache-Control: public, max-age=300`을 사용한다.
- 정상 키 교체에서는 새 Public Key를 JWKS에 게시하고 5분이 지난 후 새 Private Key로 Access Token 발급을 시작한다. 기존 Public Key는 최소 1시간 유지한다.
- API 서버가 캐시에 없는 `kid`를 받으면 JWKS를 즉시 한 번 갱신한 뒤 다시 검증한다. 갱신 후에도 `kid`에 대응하는 키가 없으면 `401 Unauthorized`로 거부한다.
- 네트워크 조회 결과만 믿고 허용 알고리즘이나 claim 검증을 생략하지 않는다.

#### Refresh Token

- 예측 불가능한 opaque random token을 사용한다.
- 원문은 DB에 저장하지 않고 SHA-256 해시를 `refresh_tokens.token_hash`에 저장한다.
- DB 행 존재 여부, `expires_at`과 `revoked_at IS NULL`로 유효성을 판단한다.
- 갱신 성공 후에도 같은 Refresh Token과 기존 `expires_at`을 유지한다.
- 만료됐거나 `revoked_at`이 기록된 토큰으로는 Access Token을 발급하지 않는다.
- 로그아웃은 현재 Token의 `revoked_at`을, 회원 탈퇴는 해당 사용자의 모든 미폐기 Token의 `revoked_at`을 기록한다.
- 만료·폐기된 행은 인증 처리 과정에서 물리 삭제하지 않고 이력으로 유지한다.
- 만료·폐기 이력의 보관 기간과 정기 물리 삭제는 초기 구현 범위에 포함하지 않고 운영 단계에서 별도로 다룬다.
- 정기 정리용 인덱스는 현재 단계에서 추가·변경하지 않는다.

### 2.3 Refresh Token Cookie

Cookie 이름과 세부 속성, Origin 검증 및 CORS 정책은 `APPROVED`다.

```http
Set-Cookie: refresh_token={opaqueToken}; Max-Age=2592000; Path=/api/auth; HttpOnly; Secure; SameSite=Lax
```

| 속성 | 값 | 이유 |
| --- | --- | --- |
| 이름 | `refresh_token` | 인증 전용 Cookie임을 명시 |
| `Max-Age` | `2592000` | 30일 |
| `Path` | `/api/auth` | 인증 관련 요청으로 전송 범위 제한 |
| `HttpOnly` | 활성화 | JavaScript에서 원문 접근 차단 |
| `Secure` | 활성화 | HTTPS 연결에서만 전송 |
| `SameSite` | `Lax` | 일반적인 교차 사이트 요청의 Cookie 전송 제한 |
| `Domain` | 지정하지 않음 | API host 전용 host-only Cookie |

- 운영·스테이징에서는 `Secure`를 반드시 활성화한다. 로컬 HTTP 개발 환경에서만 별도 환경설정으로 비활성화할 수 있으며 운영 설정과 분리한다.
- 프론트엔드와 API가 cross-site 구성이어서 `SameSite=None`이 필요해지면 CSRF 방어와 CORS 정책을 별도로 재검토한 후 변경한다.
- Cookie 기반 `POST /api/auth/refresh`와 `POST /api/auth/logout`은 `Origin`의 scheme, host, port 전체를 환경별 허용 frontend origin과 정확히 비교한다.
- 브라우저용 Refresh·Logout 요청에 `Origin`이 없거나 허용 목록과 일치하지 않으면 `403 Forbidden`, `ORIGIN_NOT_ALLOWED`로 거부한다.
- same-origin 배포에는 별도 CORS 허용이 필요하지 않다. cross-origin 배포에서는 클라이언트가 credential을 포함하고 서버가 정확한 frontend origin과 `Access-Control-Allow-Credentials: true`를 반환한다.
- credential을 허용하는 CORS 응답에는 `Access-Control-Allow-Origin: *`를 사용하지 않는다.
- 로그아웃과 회원 탈퇴로 Cookie를 제거할 때는 같은 이름, `Path`와 `Domain` 범위를 사용하고 `Max-Age=0`으로 설정한다.

### 2.4 인증 사용자와 인가

- 현재 사용자는 검증된 Access Token의 `sub`로 식별한다.
- request body나 query의 `userId`를 현재 사용자 판정에 사용하지 않는다.
- 리소스 소유자, 여행방 멤버, 방장 권한은 서버가 매 요청마다 검증한다.
- 탈퇴 사용자는 Access Token의 서명이 유효해도 인증을 거부한다.

### 2.5 폐기 범위

#### 로그아웃

1. 요청 Cookie의 Refresh Token을 해시한다.
2. 일치하는 활성 DB 행이 있다면 `revoked_at`에 현재 시각을 기록한다.
3. Refresh Token Cookie를 만료시킨다.
4. 클라이언트가 메모리의 Access Token을 제거한다.
5. 이미 폐기·만료됐거나 존재하지 않는 Cookie여도 같은 최종 상태를 만들고 성공 처리한다.

로그아웃은 현재 브라우저에 대응하는 Refresh Token 하나만 폐기한다. 다른 브라우저의 로그인은 유지한다.

#### 회원 탈퇴

1. 인증 사용자와 `oauth_accounts`의 카카오 회원번호를 조회한다.
2. 서버의 카카오 Admin Key와 회원번호로 카카오 연결 해제를 동기 호출한다.
3. 연결 해제가 성공했거나, 로컬 OAuth 정보가 남은 재시도에서 카카오 오류 코드 `-101`로 이미 해제된 상태가 확인되면 로컬 탈퇴 트랜잭션을 시작한다.
4. `users.deleted_at`을 기록하여 계정을 소프트 삭제한다.
5. 해당 사용자의 모든 미폐기 Refresh Token 행에 `revoked_at`을 기록한다.
6. 해당 사용자의 `oauth_accounts` 행을 삭제한다.
7. 로컬 트랜잭션을 커밋한 뒤 현재 브라우저의 Refresh Token Cookie를 만료시킨다.
8. 클라이언트가 메모리의 Access Token을 제거한다.
9. 이후 인증 과정에서 탈퇴 사용자를 거부한다.

- 회원 탈퇴 전에 비밀번호 입력, OAuth 재로그인 같은 별도 재인증을 요구하지 않는다.
- PlanIt은 카카오 사용자 토큰을 영구 저장하지 않으므로 연결 해제에는 서버의 카카오 Admin Key와 저장된 `provider_user_id`를 사용한다.
- 카카오 연결 해제가 실패하면 로컬 데이터를 변경하지 않고 `503 KAKAO_UNLINK_UNAVAILABLE`을 반환한다. 사용자는 탈퇴를 다시 요청할 수 있다.
- 카카오 연결 해제 성공 후 로컬 트랜잭션이 실패하면 `503 WITHDRAWAL_UNAVAILABLE`을 반환하고 Cookie를 제거하지 않는다. 트랜잭션이 롤백되어 `oauth_accounts`가 남으므로 재요청할 수 있다.
- 재요청에서 로컬 OAuth 정보가 남아 있고 카카오가 미연결 오류 코드 `-101`을 반환하면 이미 연결 해제된 상태로 처리한다. 다른 카카오 오류는 성공으로 처리하지 않는다.
- 별도 outbox나 비동기 재시도 테이블은 사용하지 않는다.

### 2.6 의도적으로 수용한 보안 제한

Refresh Token 회전과 재사용 탐지를 사용하지 않으므로 탈취된 활성 Refresh Token을 정상 사용자와 구분할 수 없다. 공격자가 먼저 또는 반복해서 사용해도 만료되거나 `revoked_at`이 기록되기 전까지 새로운 Access Token을 발급받을 수 있다.

별도 Access Token denylist 또는 사용자별 token version을 두지 않으므로 로그아웃 시 서버가 이미 발급한 Access Token을 즉시 무효화하지 않는다. 정상 클라이언트는 즉시 토큰을 제거하지만 탈취된 Access Token은 최대 15분 동안 유효할 수 있다.

이는 프로젝트에서 명시적으로 선택한 단순화다. 보안 요구가 높아지면 다음 중 하나를 새로운 변경 단위로 검토한다.

- Refresh Token rotation과 token family 기반 재사용 탐지
- sender-constrained token
- `jti` denylist 또는 사용자별 token version을 통한 Access Token 즉시 폐기

참고 기준:

- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html)
- [OWASP REST Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html)

## 3. 인증 API 목록

토큰 저장·만료·폐기 정책은 위 승인된 공통 계약을 따른다. 카카오 로그인 시작·callback 경로와 전체 화면 리다이렉트 흐름은 승인된 계약이다.

| API | Method | URL | 인증 | 상태 |
| --- | --- | --- | --- | --- |
| 카카오 로그인 시작 | `GET` | `/api/auth/kakao/authorize` | 불필요 | `APPROVED` |
| 카카오 OAuth callback | `GET` | `/api/auth/kakao/callback` | OAuth `state` 검증 | `APPROVED` |
| JWT Public Key 조회 | `GET` | `/.well-known/jwks.json` | 불필요 | `APPROVED` |
| Access Token 갱신 | `POST` | `/api/auth/refresh` | Refresh Token Cookie | `APPROVED` |
| 로그아웃 | `POST` | `/api/auth/logout` | Refresh Token Cookie | `APPROVED` |
| 현재 사용자 조회 | `GET` | `/api/users/me` | Access Token | `APPROVED` |
| 회원 탈퇴 | `DELETE` | `/api/users/me` | Access Token | `APPROVED` |

## 4. 인증 API 상세

### 4.1 카카오 로그인 시작

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/auth/kakao/authorize` |
| 설명 | 카카오 OAuth 인가 요청을 시작한다. |
| 인증 | 불필요 |
| 관련 규칙 | `BR-AUTH-01`, `BR-AUTH-02` |

- 모바일 크기의 반응형 웹을 기준으로 팝업이나 새 창을 열지 않고 현재 화면 전체를 카카오 로그인으로 이동시킨다.
- frontend 화면에서 로그인 버튼을 모달이나 바텀시트에 배치할 수는 있지만 OAuth 자체는 전체 화면 리다이렉트로 처리한다.

#### 요청

| 위치 | 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- | --- |
| Query | `returnTo` | string | N | 동일 frontend origin의 허용된 상대 경로, 기본값 `/` | 로그인 완료 후 복귀 위치 |

#### 응답

| HTTP 상태 | 조건 |
| ---: | --- |
| `302 Found` | 카카오 인가 화면으로 이동 |
| `400 Bad Request` | 허용되지 않은 `returnTo` |

처리 규칙:

1. `returnTo`가 생략되면 `/`을 사용한다.
2. 서버는 로그인 요청마다 예측 불가능한 128비트 이상의 `state`를 생성한다.
3. `state`와 검증된 `returnTo`를 10분짜리 `Secure`, `HttpOnly`, `SameSite=Lax` 임시 Cookie에 보관한다.
4. 카카오 인가 endpoint로 `302` 리다이렉트한다.

```http
HTTP/1.1 302 Found
Location: https://kauth.kakao.com/oauth/authorize?client_id={kakaoRestApiKey}&redirect_uri={encodedCallbackUri}&response_type=code&state={state}
```

- `returnTo`는 `/` 하나로 시작하는 상대 경로만 허용하며 `//`, scheme, host, 역슬래시와 제어 문자를 거부한다.
- 실제 허용 경로는 frontend route allowlist로 관리한다. 초대 링크 복귀 경로 `/invitations/{invitationToken}`을 허용하며, `invitationToken`은 Base64 URL-safe 무패딩 43자 형식만 인정한다.
- `returnTo`와 `state`를 카카오 앱 설정이나 로그에 민감 정보로 남기지 않는다.
- 카카오 REST API Key와 Client Secret은 서버 설정으로만 관리하고 응답이나 frontend bundle에 포함하지 않는다.

### 4.2 카카오 OAuth callback

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/auth/kakao/callback` |
| 설명 | 카카오 인가 코드를 검증하고 PlanIt 로그인 상태를 만든다. |
| 인증 | OAuth `state` 검증 |
| 관련 규칙 | `BR-AUTH-01`, `BR-AUTH-02` |

- callback URI는 사용자가 머무는 화면이 아니라 카카오가 인가 코드 또는 오류를 전달하는 PlanIt 백엔드 endpoint다.
- 현재 개발 환경에서는 `http://localhost:{backendPort}/api/auth/kakao/callback` 형태를 사용한다.
- 운영 도메인이 확정되면 HTTPS callback URI를 환경 설정과 카카오 Developers에 추가한다.

#### 요청

| 위치 | 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Query | `code` | string | Y | 카카오 인가 코드 |
| Query | `state` | string | Y | 로그인 시작 요청과 callback 연결 및 CSRF 방어 |
| Query | `error` | string | N | 사용자가 거부했거나 OAuth가 실패한 경우 |
| Query | `error_description` | string | N | 카카오가 전달한 오류 설명. 서버 로그용이며 frontend에 원문 전달하지 않음 |

#### 성공 처리

1. Query의 `state`와 임시 Cookie의 `state`를 상수 시간 비교로 검증한다.
2. 임시 OAuth Cookie를 즉시 제거하여 같은 브라우저에서 재사용하지 못하게 한다.
3. 인가 코드를 카카오 토큰 endpoint에 한 번만 교환한다.
4. 카카오 Access Token으로 사용자 정보 endpoint를 호출하고 앱 범위의 카카오 회원번호와 닉네임을 얻는다.
5. `oauth_accounts(provider='KAKAO', provider_user_id)`로 사용자를 조회한다.
6. 기존 연결이 없으면 카카오 닉네임의 앞뒤 공백을 제거해 `users.username`에 저장하고 `users`와 `oauth_accounts`를 하나의 트랜잭션으로 생성한다.
7. PlanIt Refresh Token을 생성하고 해시를 DB에 저장한 뒤 30일 Cookie를 설정한다.
8. PlanIt Access Token과 카카오 토큰을 URL에 포함하지 않고 검증된 `returnTo`로 `302` 이동한다.
9. frontend는 이동한 화면에서 `/api/auth/refresh`를 호출해 PlanIt Access Token을 메모리에 저장한다.

```http
HTTP/1.1 302 Found
Set-Cookie: refresh_token={opaqueToken}; Max-Age=2592000; Path=/api/auth; HttpOnly; Secure; SameSite=Lax
Location: {frontendBaseUrl}{validatedReturnTo}
```

#### 실패 처리

| 조건 | frontend 오류 코드 | 처리 |
| --- | --- | --- |
| 사용자가 카카오 동의를 취소함 | `OAUTH_ACCESS_DENIED` | 로그인 화면으로 이동 |
| `state` 누락·불일치·만료 | `OAUTH_STATE_INVALID` | 토큰 교환 없이 로그인 화면으로 이동 |
| 인가 코드 누락·교환 실패 | `OAUTH_CODE_EXCHANGE_FAILED` | 로그인 화면으로 이동 |
| 카카오 사용자 조회 실패 | `OAUTH_USER_INFO_FAILED` | 로그인 화면으로 이동 |
| 내부 사용자 생성 실패 | `OAUTH_LOGIN_FAILED` | 로그인 화면으로 이동 |

실패 시 다음 위치로 이동한다.

```http
HTTP/1.1 302 Found
Location: {frontendBaseUrl}/login?error={planItErrorCode}
```

- 카카오의 `error_description`, 인가 코드와 토큰 원문을 frontend URL에 포함하지 않는다.
- 실패 상세 원인은 서버 로그에 남기고 frontend에는 PlanIt 오류 코드만 전달한다.
- `state`가 유효하지 않으면 카카오 token endpoint를 호출하지 않는다.
- 새 사용자 생성과 OAuth 계정 연결은 원자적으로 처리한다.
- `provider_user_id`에는 카카오 회원번호를 문자열로 저장한다.
- API의 `userName`은 필수값인 DB의 `users.username VARCHAR(20)`에 대응한다. 신규 가입 시 카카오 사용자 정보의 `kakao_account.profile.nickname` 앞뒤 공백을 제거해 그대로 저장한다.
- 카카오 프로필 이미지를 S3와 `image_files`에 저장한 경우 `users.image_file_id`로 연결하고, 이미지가 없으면 `NULL`로 두어 기본 이미지를 사용한다.

### 4.3 서버와 카카오 간 외부 호출

이 절의 API는 frontend가 직접 호출하지 않는다.

#### 카카오 인가 코드 요청

| 항목 | 값 |
| --- | --- |
| Method | `GET` |
| URL | `https://kauth.kakao.com/oauth/authorize` |
| 필수 파라미터 | `client_id`, `redirect_uri`, `response_type=code`, `state` |

#### 카카오 토큰 요청

| 항목 | 값 |
| --- | --- |
| Method | `POST` |
| URL | `https://kauth.kakao.com/oauth/token` |
| Content-Type | `application/x-www-form-urlencoded;charset=utf-8` |
| 필수 파라미터 | `grant_type=authorization_code`, `client_id`, `redirect_uri`, `code` |
| 조건부 파라미터 | 카카오 앱에서 Client Secret을 활성화한 경우 `client_secret` |

- 보안을 위해 카카오 Client Secret을 활성화하고 서버에서만 전달하는 구성을 기본으로 한다.
- 토큰 요청의 `redirect_uri`는 인가 요청 값 및 카카오 Developers 등록 값과 정확히 일치해야 한다.

#### 카카오 사용자 정보 조회

| 항목 | 값 |
| --- | --- |
| Method | `GET` |
| URL | `https://kapi.kakao.com/v2/user/me` |
| Authorization | `Bearer {kakaoAccessToken}` |
| 사용 필드 | `id`, `kakao_account.profile.nickname` |

- PlanIt 사용자 연결에는 앱 범위에서 고유한 카카오 `id`와 사용자 이름으로 저장할 `kakao_account.profile.nickname`을 사용한다.
- 카카오 Developers에서 닉네임 동의항목을 필수로 구성한다. 카카오 닉네임은 최대 20자이며 앞뒤 공백을 제거한 값이 비어 있거나 누락되면 필수 사용자 정보를 가져오지 못한 것으로 보고 `OAUTH_USER_INFO_FAILED` 처리한다.
- 카카오 Access Token과 Refresh Token은 PlanIt Token으로 사용하지 않고 frontend에 전달하지 않는다.
- 사용자 연결 완료 후 카카오 토큰을 영구 저장하지 않는다.

### 4.4 JWT Public Key 조회

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/.well-known/jwks.json` |
| 인증 | 불필요 |
| 설명 | Access Token 서명 검증에 사용할 RSA Public Key 목록을 제공한다. |

#### 요청

request header, query와 body에 필수 입력값이 없다.

#### 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: public, max-age=300
```

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "2026-09-key-01",
      "n": "{base64urlEncodedModulus}",
      "e": "AQAB"
    }
  ]
}
```

- 이 응답은 표준 JWK Set 구조를 사용하므로 공통 API envelope를 적용하지 않는다.
- 정상 교체 중에는 새 Public Key와 유지 기간이 끝나지 않은 기존 Public Key가 `keys`에 함께 포함될 수 있다.
- Private Key 또는 이를 복원할 수 있는 필드는 응답하지 않는다.

### 4.5 Access Token 갱신

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/auth/refresh` |
| 설명 | 유효한 Refresh Token으로 15분 Access Token을 발급한다. |
| 인증 | Refresh Token Cookie |
| 멱등성 | 보장하지 않음. 요청마다 서로 다른 `jti`의 Access Token을 발급할 수 있음 |

#### 요청

request body는 없다.

```http
POST /api/auth/refresh
Cookie: refresh_token={opaqueToken}
Origin: https://{allowedFrontendOrigin}
```

#### 성공 응답

```http
HTTP/1.1 200 OK
Cache-Control: no-store
Pragma: no-cache
Content-Type: application/json
```

```json
{
  "code": "ACCESS_TOKEN_ISSUED",
  "message": "Access Token이 발급되었습니다.",
  "data": {
    "accessToken": "{accessToken}",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `ACCESS_TOKEN_ISSUED` | Access Token 발급 성공 |
| `401 Unauthorized` | `REFRESH_TOKEN_REQUIRED` | Cookie가 없음 |
| `401 Unauthorized` | `REFRESH_TOKEN_INVALID` | 행이 없거나, 만료됐거나, 사용자가 없거나 탈퇴하여 인증할 수 없음 |
| `403 Forbidden` | `ORIGIN_NOT_ALLOWED` | `Origin`이 없거나 허용된 frontend origin과 일치하지 않음 |

- 성공해도 Refresh Token을 새로 발급하거나 만료 시간을 연장하지 않는다.
- 성공 응답에서 Refresh Token Cookie를 다시 설정하지 않고 DB의 `issued_at`, `expires_at`도 변경하지 않는다.
- 만료됐거나 `revoked_at`이 기록된 행이면 `REFRESH_TOKEN_INVALID`을 반환하며 행은 이력으로 유지한다.
- 사용자 행이 없거나 `deleted_at`이 설정된 상태에서 미폐기 Refresh Token 행이 남아 있다면 `revoked_at`을 기록하고 `REFRESH_TOKEN_INVALID`을 반환한다.
- 같은 Refresh Token으로 요청을 반복하거나 동시에 요청하면 서로 다른 Access Token이 발급될 수 있으며 각각 15분 동안 유효하다.
- 원문 Refresh Token은 로그에 기록하지 않는다.

### 4.6 로그아웃

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `POST` |
| URL | `/api/auth/logout` |
| 설명 | 현재 브라우저의 Refresh Token에 폐기 시각을 기록한다. |
| 인증 | Refresh Token Cookie. 없어도 최종 결과는 동일하게 처리 |
| 멱등성 | 자연 멱등 |

#### 요청

request body는 없다.

```http
POST /api/auth/logout
Cookie: refresh_token={opaqueToken}
Origin: https://{allowedFrontendOrigin}
```

#### 응답

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `204 No Content` | - | 활성 토큰 폐기 또는 이미 폐기·만료·미등록·Cookie 없음의 최종 상태와 Cookie 제거 완료 |
| `403 Forbidden` | `ORIGIN_NOT_ALLOWED` | `Origin`이 없거나 허용된 frontend origin과 일치하지 않음 |
| `503 Service Unavailable` | `LOGOUT_UNAVAILABLE` | DB 조회 또는 `revoked_at` 기록에 실패하여 로그아웃을 완료하지 못함 |

- `204` 응답에 body를 포함하지 않는다.
- 활성 토큰이 있으면 `revoked_at IS NULL`과 미만료 조건으로 `revoked_at`에 현재 시각을 기록한다.
- 이미 폐기·만료됐거나 DB에 없는 Token 및 Cookie가 없는 요청도 DB 처리가 정상적으로 끝났다면 `204`로 처리한다.
- DB 처리가 정상적으로 끝난 뒤 Cookie는 발급 시와 동일한 이름, `Path`, `Domain` 범위를 사용하고 `Max-Age=0`으로 제거한다.
- DB 장애로 `503 LOGOUT_UNAVAILABLE`을 반환할 때는 재시도할 수 있도록 Cookie를 제거하지 않는다.
- `403 ORIGIN_NOT_ALLOWED`에서는 DB와 Cookie 상태를 변경하지 않는다.
- `204` 응답 후 클라이언트는 메모리의 Access Token과 사용자 상태를 제거한다.
- 로그아웃은 현재 Cookie에 대응하는 Refresh Token 하나만 폐기하고 다른 브라우저의 Token은 유지한다.
- 이미 발급된 Access Token은 즉시 폐기하지 않으며 기존 계약대로 최대 15분간 유효할 수 있다.
- Refresh와 Logout이 동시에 처리되면 먼저 완료된 Refresh가 Access Token을 발급할 수 있으며, 이후 Logout은 추가 갱신에 사용할 Refresh Token을 폐기한다.

### 4.7 회원 탈퇴

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `DELETE` |
| URL | `/api/users/me` |
| 설명 | 카카오 연결을 해제한 뒤 현재 사용자를 소프트 삭제하고 인증 정보를 제거한다. |
| 인증 | Access Token 필수 |
| 인가 | 본인만 가능 |
| 재인증 | 요구하지 않음 |
| 멱등성 | 카카오의 이미 해제된 상태를 성공으로 취급하고 로컬 탈퇴를 재시도할 수 있음 |

#### 요청

request body는 없으며 별도 재인증 또는 확인용 credential을 요구하지 않는다.

```http
DELETE /api/users/me
Authorization: Bearer {accessToken}
```

#### 응답

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `204 No Content` | - | 카카오 연결 해제, 소프트 삭제, OAuth 정보 삭제, 전체 활성 Refresh Token 폐기 및 Cookie 제거 완료 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `503 Service Unavailable` | `KAKAO_UNLINK_UNAVAILABLE` | 카카오 연결 해제에 실패하여 로컬 탈퇴를 수행하지 않음 |
| `503 Service Unavailable` | `WITHDRAWAL_UNAVAILABLE` | 카카오 연결 해제 후 로컬 탈퇴 트랜잭션에 실패함 |

- 회원 탈퇴 시 모든 활성 여행 멤버십에 `left_at`을 기록하고 비활성화한다. 여행 시작 전에 한 번이라도 2명 이상이었던 여행방이 1명만 남으면 여행방과 마지막 멤버십을 함께 종료하지만, 여행 중·후에는 회원 탈퇴로 인원이 감소해도 여행방을 자동 삭제하지 않는다.
- 회원 탈퇴 시 포토 미션 사진과 관련 완료 상태를 갱신하는 세부 처리는 포토 미션 계약에서 정의한다.
- 카카오 연결 해제는 `POST https://kapi.kakao.com/v1/user/unlink`를 사용하며 `Authorization: KakaoAK {ADMIN_KEY}`, `Content-Type: application/x-www-form-urlencoded;charset=utf-8`, `target_id_type=user_id`, `target_id={providerUserId}`를 전달한다.
- 연결 해제 성공 후 `oauth_accounts` 행을 삭제한다. 행만 먼저 삭제하지 않는다.
- `KAKAO_UNLINK_UNAVAILABLE` 응답은 재시도 가능하다.
- 로컬 OAuth 정보가 남은 재시도에서 카카오 오류 코드 `-101`은 이미 연결 해제된 상태로 처리하지만 다른 카카오 오류는 성공으로 처리하지 않는다.
- 카카오 연결 해제 후 `users.deleted_at` 기록, 모든 활성 Refresh Token의 `revoked_at` 기록과 `oauth_accounts` 삭제를 하나의 로컬 트랜잭션으로 처리한다.
- 로컬 트랜잭션이 실패하면 전체 변경을 롤백하고 `WITHDRAWAL_UNAVAILABLE`을 반환하며 재시도를 위해 Cookie를 제거하지 않는다.
- 로컬 트랜잭션 커밋 후 현재 Refresh Token Cookie를 `Max-Age=0`으로 제거한다.
- `204`를 받은 클라이언트는 메모리의 Access Token과 사용자 상태를 제거한다. 이후 서버는 `users.deleted_at`이 설정된 사용자의 Access Token을 거부한다.
- `204` 응답에 body를 포함하지 않는다.

### 4.8 현재 사용자 조회

#### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 상태 | `APPROVED` |
| Method | `GET` |
| URL | `/api/users/me` |
| 설명 | Access Token으로 현재 로그인 사용자 상태를 복원한다. |
| 인증 | Access Token 필수 |

#### 성공 응답

```json
{
  "code": "CURRENT_USER_RETRIEVED",
  "message": "현재 사용자 정보를 조회했습니다.",
  "data": {
    "publicId": "01991f6e-7300-7b21-a3cc-1436db3df95e",
    "userName": "플랜잇사용자",
    "profileImageUrl": "https://example.invalid/presigned-profile-image"
  }
}
```

| HTTP 상태 | API 코드 | 조건 |
| ---: | --- | --- |
| `200 OK` | `CURRENT_USER_RETRIEVED` | 활성 사용자 조회 성공 |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Access Token이 없거나 유효하지 않음 |
| `401 Unauthorized` | `USER_WITHDRAWN` | 탈퇴 처리된 사용자임 |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 내부 오류 |

#### 처리 규칙

- 현재 사용자는 검증된 Access Token의 `sub`인 UUIDv7 `publicId`로 조회한다.
- `userName`은 `users.username`에 저장된 필수 카카오 닉네임이며 `null`을 반환하지 않는다.
- `users.image_file_id`가 있으면 권한 확인 후 5분짜리 조회 URL을 발급하고, 없으면 서비스 기본 프로필 이미지 URL을 반환한다.
- 내부 BIGINT `users.id`, 카카오 사용자 ID와 OAuth 정보를 응답하지 않는다.

## 5. 남은 결정 사항

1. 로컬 frontend·backend 실제 포트와 배포 후 사용할 실제 도메인 값

Refresh Token 만료·폐기 이력의 보관 기간, 정기 물리 삭제와 관련 인덱스는 현재 범위에서 제외한 후속 운영 과제다.
