# 서버 세션 인증

인증 방식은 Spring Security + Spring Session JDBC입니다. 세션은 기존 PostgreSQL에 저장하며 브라우저에는 `owlly_session` 쿠키만 전달합니다. JWT는 사용하지 않습니다.

## 이번 구현 범위

- 공통 `SessionPrincipal(userId)`, 세션 생성·복원·폐기
- `GET /api/v1/auth/session`, `DELETE /api/v1/auth/session`
- 역할별 접근 제어, 요청마다 최신 계정 상태·역할 확인
- CSRF, Origin/CORS 허용 목록, 공통 JSON 401/403 오류

학생 가입·자동 로그인, 이메일 OTP 학생 재로그인(API-08), 발급 계정의 비밀번호를 검증하는 관계자 로그인(API-09)을 구현했습니다. 공통 행정동 서비스가 가입에 연결되어 있으며 2026-11-11 사용자가 실제 가입·DB 행정동 연결·세션·로그아웃을 수동 확인했습니다. 같은 날 기존 학생의 실제 학교 이메일 재인증 → API-08 로그인 → 기존 계정 세션 조회도 사용자가 확인했습니다. 학생 로그인은 기존 STUDENT/ACTIVE 계정을 검증하고, 관계자 로그인은 기존 STAFF/ACTIVE 계정과 BCrypt 해시를 검증합니다. 로그인으로 계정·학생 프로필을 새로 만들지 않습니다. 관계자 로그인도 같은 날 사용자가 안내한 수동 확인을 완료했다고 확인했습니다.

## 후속 가입·로그인 연결

1. 자격(OTP 증표 또는 비밀번호)과 계정 상태를 검증합니다.
2. 가입 시 `consumeGrant()`와 사용자·프로필 저장을 같은 트랜잭션에서 처리합니다. 학생 로그인은 증표 소비와 로그인 멱등 기록을 같은 트랜잭션에서 처리하며, 실패 시 소비가 롤백됩니다.
   관계자 로그인은 실패 횟수·잠금·멱등 기록을 트랜잭션에서 커밋한 뒤 예상된 401/403/429 오류를 반환합니다. 저장 중 예기치 않은 오류는 롤백합니다.
3. 위 트랜잭션이 **성공적으로 커밋된 뒤** `SessionService.createSession(user, request, response)`를 호출합니다. 이 메서드는 자격 검증을 대신하지 않습니다.
4. 응답은 `ApiResponse.of(sessionResponse)`와 `Cache-Control: no-store`를 사용합니다. 세션 쿠키는 Spring Session이 설정합니다.

로그인 시 기존 세션 ID와 CSRF 토큰을 교체합니다. Principal에는 사용자 ID만 보관하며, JPA 엔티티·이메일·비밀번호·OTP·인증 증표는 세션에 저장하지 않습니다. 인증된 사용자 ID는 컨트롤러에서 `@AuthenticationPrincipal SessionPrincipal principal`의 `principal.userId()`로 얻습니다. 클라이언트가 보낸 userId를 인증 정보로 사용하지 않습니다.

## 환경 설정

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SESSION_TIMEOUT` | `30d` | 서버 유휴 만료 30일. 지속 쿠키의 Max-Age도 동일 설정 사용 |
| `SESSION_COOKIE_SECURE` | `true` | localhost HTTP 개발에서만 `false` |
| `ALLOWED_ORIGINS` | localhost의 3000, 5173, 8080 포트 | 쉼표로 구분한 정확한 Origin. 운영 도메인으로 교체 |
| `SESSION_SCHEMA_INIT` | `always` | 로컬 최초 스키마 생성. 운영 스키마 배포 후 `never` |

Spring Session의 PostgreSQL 스키마는 의존성의 `org/springframework/session/jdbc/schema-postgresql.sql`입니다. `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` 테이블과 인덱스를 생성합니다. 이 인프라 스키마는 JPA의 `ddl-auto`로 생성되지 않습니다. 운영에서는 위 SQL을 배포 절차로 적용하고 자동 초기화를 끕니다.

기존 `.env`의 `SESSION_TIMEOUT=30m`도 `30d`로 바꿉니다. 서버 유휴 만료는 요청 시 연장되고 지속 쿠키는 발급 시점부터 설정 기간 동안 유지됩니다. 기존에 발급된 쿠키·세션이 자동으로 30일로 바뀌지는 않으므로 재로그인해 새 쿠키를 받습니다. 로그아웃은 서버 세션과 쿠키를 즉시 폐기합니다.

학생 로그인 재시도용 `student_login_receipts`는 업무 ERD와 구분한 인프라 테이블입니다. 키·요청 HMAC·사용자/대학 ID·증표 만료만 저장하며, 인증 증표·쿠키·CSRF 원문은 저장하지 않습니다. 운영에서는 이 테이블의 스키마도 별도 반영해야 합니다.

관계자 로그인에는 `staff_login_attempts`(이메일 HMAC 기준 실패 횟수·잠금)와 `staff_login_receipts`(요청 키·HMAC·결과·만료) 인프라 테이블을 추가합니다. 로컬 `ddl-auto=update`에서는 서버 시작 시 생성됩니다. 스키마 자동 갱신을 끈 환경은 두 테이블과 엔티티에 선언된 인덱스를 배포 시 반영해야 합니다. 이메일·비밀번호·비밀번호 해시·세션/CSRF 원문을 이 테이블에 저장하지 않습니다.

로그인은 실패·재시도 기록을 조회할 때 행 잠금을 커밋까지 유지합니다. 만료 기록 정리는 `FOR UPDATE SKIP LOCKED`로 사용 중인 행을 건너뛰고 나머지만 삭제합니다. 로그인과 정리가 겹쳐도 기록이 중간에 삭제되어 500 오류가 나지 않도록 보호하며, 건너뛴 기록은 다음 정리에서 처리합니다.

화면 정의서 v0.4 A-00 기준으로 새 비밀번호 실패 5회에 30분 잠금입니다. 잠금 중 올바른 비밀번호도 차단하고, 성공 또는 잠금 만료 시 횟수를 초기화합니다. `STAFF_LOGIN_MAX_FAILURES=5`, `STAFF_LOGIN_LOCK_SECONDS=1800`, `STAFF_LOGIN_RECEIPT_TTL_SECONDS=1800`은 기본값이 있어 기존 `.env`에 추가하지 않아도 동작합니다. 재시도 기록 유효기간 30분은 구현 설정이며 피그마의 별도 요구사항은 아닙니다. 기존 `VERIFICATION_SECRET`(32바이트 이상)을 HMAC에 함께 사용합니다.

## 클라이언트 요청

- 브라우저 fetch는 `credentials: 'include'`를 사용합니다.
- 공개 인증 POST에도 등록된 `Origin`이 필수입니다. 브라우저는 자동 전송하며 curl/Postman은 직접 지정합니다.
- 로그인 응답 또는 `GET /api/v1/auth/session`의 `data.csrfToken`을 변경 요청의 `X-CSRF-Token` 헤더에 보냅니다. 로그인 후에는 새 토큰을 사용합니다.
- `GET /auth/session`은 본인 사용자 정보·유휴 만료 시각·CSRF 토큰을 반환하며 세션 ID는 반환하지 않습니다.
- 가입·학생/관계자 로그인·현재 세션 조회 응답의 `data.user`는 `universityId`와 `universityName`을 함께 제공합니다.
- 로그아웃은 `DELETE /auth/session`에 UUID `Idempotency-Key`와 로그인 중이면 CSRF 토큰을 보냅니다. 204 응답 후 클라이언트의 사용자·CSRF 상태를 비웁니다. 이미 만료/로그아웃된 요청의 재시도도 204입니다.
- 401은 세션 없음/만료/계정 삭제·탈퇴입니다. 403 FORBIDDEN은 권한·CSRF·Origin 오류, 403 ACCOUNT_SUSPENDED는 정지 상태입니다.

공개 경로는 대학·프로필 선택값 GET, 명시된 인증 POST, Swagger 및 health입니다. 거점·은행·`/me/**`·귀가·동승 신청·팟·방·송금 경로는 STUDENT, `/staff/**`·`/demand/**`는 STAFF입니다. 팟·방·송금의 본인/대표/멤버별 권한은 역할 검사 외에 각 기능에서 별도로 검증해야 합니다.

정지 계정은 현재 세션 조회·로그아웃만 허용합니다. 정산 복구용 예외 접근, 절대 세션 만료·재인증, 동시 로그인 제한은 아직 정책이 확정되지 않았습니다.

## 검증

```powershell
.\gradlew.bat test --tests '*SessionIntegrationTest' --tests '*GlobalExceptionHandlerTest' --tests '*EmailVerificationServiceTest' --tests '*PartyGrouperTest'
```

세션 통합 테스트는 격리된 H2 DB와 실제 Spring Session JDBC 필터를 사용합니다. 사용자 조회와 메일 발급 서비스는 mock으로 대체합니다. PostgreSQL advisory lock·메일 발송·전체 애플리케이션 검증은 별도 환경에서 수행해야 합니다.

`StaffLoginIntegrationTest`는 실제 PostgreSQL·BCrypt·JDBC 세션으로 로그인/로그아웃, 동시 실패·같은 키 재시도, 잠금·해제, 변경된 비밀번호·계정 상태 재검사와 역할 차단을 검증합니다. 정리 작업을 별도 트랜잭션에서 실행해 로그인 중인 기록 보존, 미사용 만료 기록 삭제, 유효한 기록·활성 잠금 보존도 검증합니다. 정리 테스트의 삭제는 롤백해 기존 로컬 데이터에 영향을 주지 않습니다. 관계자 계정 준비·로그인 방법은 [개발 환경 안내](getting-started.md#관계자-로그인-테스트)를 참고하세요.
