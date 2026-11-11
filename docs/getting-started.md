# Getting Started

로컬 개발 환경 세팅 가이드.

## 사전 준비

- JDK 21
- Docker (로컬 DB 실행용)

## 1. 환경변수(.env) 설정

`.env.example`을 복사해 `.env`를 만듭니다. (`docker-compose.yml`과 애플리케이션이 같은 값을 공유합니다.)

```bash
cp .env.example .env
```

기본값:

```env
DB_USERNAME=owlly
DB_PASSWORD=owlly
# 아래는 기본값이 있어 로컬 기본 설정이면 생략 가능
# DB_HOST=localhost
# DB_PORT=5432
# DB_NAME=owlly
```

> `.env`는 커밋 금지 — `.gitignore`에 포함되어 있습니다.

학교 이메일 인증을 테스트하려면 복사한 **`.env`**의 이메일 설정을 채웁니다. 이미 `.env`가 있다면 덮어쓰지 말고 `.env.example`의 이메일 설정 항목만 추가하세요.

`.env`에 다음 항목을 입력하고 빈칸을 각자의 실제 설정값으로 채웁니다.

```env
VERIFICATION_SECRET=
MAIL_FROM=
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
```

- Gmail 계정의 2단계 인증을 켜고 앱 비밀번호를 발급받아 `SPRING_MAIL_PASSWORD`에 공백 없이 입력합니다.
- `MAIL_FROM`과 `SPRING_MAIL_USERNAME`에 발송용 Gmail 주소를 입력합니다. 호스트와 포트는 예시의 `smtp.gmail.com`, `587`을 사용합니다.
- `VERIFICATION_SECRET`에는 32바이트 이상의 무작위 비밀값을 입력합니다. PowerShell에서는 다음 명령으로 생성할 수 있습니다.

```powershell
[Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

`.env.example`의 계정과 비밀번호는 예시이므로 복사한 후 실제 값으로 바꿔야 합니다. 실제 계정·비밀번호·비밀값은 `.env.example`에 적지 않습니다. 설정을 변경했다면 서버를 재시작하세요.

## 2. 로컬 DB(PostgreSQL) 띄우기

`docker-compose.yml`이 `.env`의 값으로 PostgreSQL 컨테이너를 생성합니다.

```bash
docker compose up -d
```

데이터는 `owlly-postgres-data` 볼륨에 영구 저장되어 컨테이너를 내렸다 올려도 유지됩니다.

컨테이너 관리:

```bash
docker compose stop       # 중지
docker compose down       # 중지 + 컨테이너 삭제 (볼륨 데이터는 유지)
docker compose down -v    # 중지 + 컨테이너 + 볼륨 삭제 (데이터 초기화)
docker compose logs -f    # 로그 확인
docker compose ps         # 상태 확인
```

## 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

빌드 결과물(jar)로 실행하려면:

```bash
./gradlew build -x test
java -jar build/libs/owlly-0.0.1-SNAPSHOT.jar
```

`spring-dotenv`가 `.env`를 자동으로 읽어 DB에 접속합니다.

## 접속 확인

- API 서버: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

## 참고

- 학교 이메일 인증에는 `VERIFICATION_SECRET`(32바이트 이상 무작위 비밀값), `MAIL_FROM`과 `SPRING_MAIL_HOST` 등 `.env.example`의 SMTP 설정이 필요합니다. SMTP 서버·계정이 설정되지 않으면 인증 메일 발급은 실패합니다. 인증 증표는 10분 유효하며 가입 서비스는 같은 트랜잭션에서 `consumeGrant`를 호출해야 합니다.
- 강남대학교(`kangnam.ac.kr`), 경희대학교(`khu.ac.kr`), 단국대학교(`dankook.ac.kr`), 명지대학교(`mju.ac.kr`), 한국외국어대학교(`hufs.ac.kr`)와 해당 이메일 도메인은 실행 시 없는 항목만 추가됩니다. 기존 항목의 활성 여부와 소속은 자동으로 변경하지 않습니다.
- 탈퇴 후 이메일 재가입 정책은 회원가입·탈퇴 구현 시 DB 고유 제약 변경이 필요하며 현재 `users.email`의 단일 UNIQUE는 아직 변경되지 않았습니다.

- 인증은 Spring Security + Spring Session JDBC의 서버 세션 방식입니다. 상세 연결 방법은 [세션 인증](auth-session.md)을 참고하세요.
- 기존 `.env`에는 `.env.example`의 세션 설정도 추가하세요. localhost HTTP 개발은 `SESSION_COOKIE_SECURE=false`, 운영 HTTPS는 `true`를 사용합니다.
- 이메일 인증 POST에도 허용된 `Origin` 헤더가 필요합니다. Swagger에서는 `http://localhost:8080`, 프런트에서는 자신의 Origin을 `ALLOWED_ORIGINS`에 포함하세요. curl/Postman은 `Origin: http://localhost:8080`을 직접 추가합니다.
- 대학·프로필 선택값·공개 인증·Swagger·health 이외의 경로는 인증이 필요합니다. 학생 가입과 이메일 OTP 학생 로그인은 구현되어 있습니다. 공통 행정동 서비스가 가입에 연결되어 있고 실제 가입 연동 검증도 완료했습니다. 기존 `.env`의 `SESSION_TIMEOUT`도 `30d`로 갱신하세요. 지속 쿠키 수명은 같은 값을 따릅니다.
- `SessionIntegrationTest`는 H2 기반 JDBC 세션 통합 테스트입니다. 기존 `OwllyApplicationTests.contextLoads`는 실제 PostgreSQL과 `.env` 설정이 필요합니다.

## 학생 가입 테스트

**현재 API-02·API-07은 완료입니다.** 2026-11-11 사용자가 실제 가입·세션 조회·DB 행정동 연결·로그아웃을 수동 확인했습니다. develop의 공통 행정동 판별 서비스가 가입용 인터페이스에 연결되어 있습니다. 카카오 키 누락·장애는 `503 UPSTREAM_UNAVAILABLE`, 판별 실패는 `422 LOCATION_UNRESOLVABLE`입니다. 자동 테스트에서는 연결 지점을 mock으로 대체합니다.

1. 로컬 `.env`의 `KAKAO_REST_API_KEY`를 설정합니다. 공통 `AdministrativeAreaResolver`가 `DropoffLocationService`를 구현하므로 가입용 어댑터를 추가할 필요는 없습니다.
2. 공통 서비스는 카카오 행정동(H) 코드를 DB 공식 코드에 연결하고, 없는 시·구·동은 등록한 뒤 `resolveDong(latitude, longitude)`에서 DONG 엔티티를 반환합니다. 별도 행정구역 시드는 필요하지 않습니다.
3. Swagger에서 대학 조회 → 이메일 발급(202) → 코드 검증(200) → 증표로 가입(201)을 진행합니다. 각 명령에 서로 다른 UUID `Idempotency-Key`를 사용합니다. 가입 재시도만 같은 키와 본문을 유지합니다.
4. `GET /api/v1/profile-options`의 코드 `FEMALE`/`MALE`를 사용합니다. 이름은 2~20자이며 `sameGenderDefault`는 `true`/`false` 모두 허용합니다. 선택한 값을 저장하고, 생략/null이면 true로 처리합니다. 선택값 조회 응답의 true는 초기 기본값입니다.
5. 가입 응답의 `data.csrfToken`과 `owlly_session` 쿠키로 세션 조회·보호 API·로그아웃을 확인합니다. Swagger에 CSRF 헤더 입력란이 없는 경우 Postman/curl에서 `X-CSRF-Token`과 쿠키를 전달합니다. 로그아웃 후 세션 조회는 401입니다.

가입과 현재 세션 조회 응답의 `data.user`에 `universityName`을 추가했습니다. 하차 동 이름을 반환하는 프로필 조회·수정 API는 후속 브랜치에서 구현합니다.

`StudentRegistrationIntegrationTest`는 실제 PostgreSQL을 사용하며 공통 행정동 판별 계약만 mock으로 대체합니다. 테스트별 무작위 이메일·행정동을 만들고 종료 시 그 데이터와 세션만 정리합니다. 위치 서비스 오류·증표 소비 롤백·계정/세션 미생성을 검증합니다. 자동 테스트 통과는 실제 카카오 가입 검증 완료를 뜻하지 않습니다. `.env`에 실제 DB/암호화 키 설정이 필요하고, 주석은 `//`가 아닌 `#`로 작성합니다.

## 기존 학생 로그인 테스트

1. 이미 가입된 학교 이메일로 이메일 발급 → 코드 검증을 다시 진행합니다. 가입에 사용한 증표를 재사용하지 않습니다.
2. 받은 `verificationToken`으로 `POST /api/v1/auth/student-sessions`를 호출합니다. 허용 Origin·UUID Idempotency-Key와 JSON 본문이 필요합니다.
3. 200 응답의 대학명·CSRF 토큰, `owlly_session` 쿠키의 Max-Age=2592000(30일 기본값), 세션 조회를 확인합니다. 브라우저는 credentials/include 또는 withCredentials를 사용합니다.
4. 미가입은 404 ACCOUNT_NOT_FOUND입니다. 증표 소비가 롤백되므로 증표 유효기간 내 프로필을 입력해 가입할 수 있습니다. STAFF·탈퇴·프로필 누락·대학 불일치는 403 ACCOUNT_UNAVAILABLE, 정지는 403 ACCOUNT_SUSPENDED입니다.
5. 같은 로그인 재시도는 같은 키·증표를 유지합니다. 인증 쿠키를 함께 보내는 재시도에는 해당 세션의 X-CSRF-Token도 필요합니다. 다른 키의 소비된 증표는 409, 같은 키의 다른 증표도 409입니다.
6. 로그아웃 후 기존 쿠키로 세션 조회 시 401인지 확인합니다. 앱 재진입은 먼저 GET /api/v1/auth/session으로 확인하고 성공하면 홈으로 이동합니다.

`StudentLoginIntegrationTest`는 PostgreSQL의 실제 증표 소비·멱등 기록·잠금·세션을 검증합니다. 이메일 발급·검증 → 로그인 연결 테스트는 발송기만 mock으로 대체하므로 실제 메일을 보내지 않습니다. 별도로 2026-11-11 사용자가 Swagger에서 실제 학교 이메일 재인증 → API-08 로그인 → 기존 계정 세션 조회를 수동 확인했습니다. 로그인 전용 추가 속도 제한 기준은 미정이며, 기존 발송·인증 실패 제한은 적용됩니다.
