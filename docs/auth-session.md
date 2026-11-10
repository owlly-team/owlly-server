# 서버 세션 인증

인증 방식은 Spring Security + Spring Session JDBC입니다. 세션은 기존 PostgreSQL에 저장하며 브라우저에는 `owlly_session` 쿠키만 전달합니다. JWT는 사용하지 않습니다.

## 이번 구현 범위

- 공통 `SessionPrincipal(userId)`, 세션 생성·복원·폐기
- `GET /api/v1/auth/session`, `DELETE /api/v1/auth/session`
- 역할별 접근 제어, 요청마다 최신 계정 상태·역할 확인
- CSRF, Origin/CORS 허용 목록, 공통 JSON 401/403 오류

가입·학생 로그인·관계자 로그인 API는 아직 없습니다. 테스트용 로그인 컨트롤러는 테스트 컨텍스트에만 있으며 운영 코드에 포함되지 않습니다.

## 후속 가입·로그인 연결

1. 자격(OTP 증표 또는 비밀번호)과 계정 상태를 검증합니다.
2. 가입 시 `consumeGrant()`와 사용자·프로필 저장을 같은 트랜잭션에서 처리합니다.
3. 위 트랜잭션이 **성공적으로 커밋된 뒤** `SessionService.createSession(user, request, response)`를 호출합니다. 이 메서드는 자격 검증을 대신하지 않습니다.
4. 응답은 `ApiResponse.of(sessionResponse)`와 `Cache-Control: no-store`를 사용합니다. 세션 쿠키는 Spring Session이 설정합니다.

로그인 시 기존 세션 ID와 CSRF 토큰을 교체합니다. Principal에는 사용자 ID만 보관하며, JPA 엔티티·이메일·비밀번호·OTP·인증 증표는 세션에 저장하지 않습니다. 인증된 사용자 ID는 컨트롤러에서 `@AuthenticationPrincipal SessionPrincipal principal`의 `principal.userId()`로 얻습니다. 클라이언트가 보낸 userId를 인증 정보로 사용하지 않습니다.

## 환경 설정

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `SESSION_TIMEOUT` | `30m` | 요청마다 갱신되는 유휴 만료. 운영 정책 확정값이 아닌 개발 기본값 |
| `SESSION_COOKIE_SECURE` | `true` | localhost HTTP 개발에서만 `false` |
| `ALLOWED_ORIGINS` | localhost의 3000, 5173, 8080 포트 | 쉼표로 구분한 정확한 Origin. 운영 도메인으로 교체 |
| `SESSION_SCHEMA_INIT` | `always` | 로컬 최초 스키마 생성. 운영 스키마 배포 후 `never` |

Spring Session의 PostgreSQL 스키마는 의존성의 `org/springframework/session/jdbc/schema-postgresql.sql`입니다. `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` 테이블과 인덱스를 생성합니다. 이 인프라 스키마는 JPA의 `ddl-auto`로 생성되지 않습니다. 운영에서는 위 SQL을 배포 절차로 적용하고 자동 초기화를 끕니다.

## 클라이언트 요청

- 브라우저 fetch는 `credentials: 'include'`를 사용합니다.
- 공개 인증 POST에도 등록된 `Origin`이 필수입니다. 브라우저는 자동 전송하며 curl/Postman은 직접 지정합니다.
- 로그인 응답 또는 `GET /api/v1/auth/session`의 `data.csrfToken`을 변경 요청의 `X-CSRF-Token` 헤더에 보냅니다. 로그인 후에는 새 토큰을 사용합니다.
- `GET /auth/session`은 본인 사용자 정보·유휴 만료 시각·CSRF 토큰을 반환하며 세션 ID는 반환하지 않습니다.
- 로그아웃은 `DELETE /auth/session`에 UUID `Idempotency-Key`와 로그인 중이면 CSRF 토큰을 보냅니다. 204 응답 후 클라이언트의 사용자·CSRF 상태를 비웁니다. 이미 만료/로그아웃된 요청의 재시도도 204입니다.
- 401은 세션 없음/만료/계정 삭제·탈퇴입니다. 403 FORBIDDEN은 권한·CSRF·Origin 오류, 403 ACCOUNT_SUSPENDED는 정지 상태입니다.

공개 경로는 대학·프로필 선택값 GET, 명시된 인증 POST, Swagger 및 health입니다. 거점·은행·`/me/**`는 STUDENT, `/staff/**`·`/demand/**`는 STAFF입니다. 팟·방·송금의 본인/대표/멤버별 권한은 각 기능에서 별도로 검증해야 합니다. 단순 로그인 검사로 대신할 수 없습니다.

정지 계정은 현재 세션 조회·로그아웃만 허용합니다. 정산 복구용 예외 접근, 절대 세션 만료·재인증, 동시 로그인 제한은 아직 정책이 확정되지 않았습니다.

## 검증

```powershell
.\gradlew.bat test --tests '*SessionIntegrationTest' --tests '*GlobalExceptionHandlerTest' --tests '*EmailVerificationServiceTest' --tests '*PartyGrouperTest'
```

세션 통합 테스트는 격리된 H2 DB와 실제 Spring Session JDBC 필터를 사용합니다. 사용자 조회와 메일 발급 서비스는 mock으로 대체합니다. PostgreSQL advisory lock·메일 발송·전체 애플리케이션 검증은 별도 환경에서 수행해야 합니다.
