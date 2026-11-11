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

학생 기능 테스트에 사용할 계정이 없는 경우 한 번 가입합니다. 학교 이메일 수신이 가능해야 하며, 위 SMTP 설정과 `.env`의 `KAKAO_REST_API_KEY`가 필요합니다.

1. `GET /api/v1/universities`에서 대학과 이메일 도메인을 조회합니다.
2. `POST /api/v1/auth/email-verifications`로 인증번호를 받고, `POST /api/v1/auth/email-verifications/{id}/verify`로 검증해 `verificationToken`을 받습니다.
3. `POST /api/v1/auth/student-registrations`에 증표와 `profile`을 보냅니다. `realName`은 2~20자, `genderCode`는 `GET /api/v1/profile-options`의 `FEMALE`/`MALE`, `sameGenderDefault`는 동성 매칭 기본값입니다. `defaultDropoff`에는 하차 지점의 `latitude`·`longitude`를 넣습니다. 행정동은 서버가 연결하므로 별도 시드는 필요 없습니다.
4. 가입 성공(201) 시 자동 로그인됩니다. 응답의 `data.user`와 `data.csrfToken`을 사용해 담당 기능 테스트를 진행합니다.

각 POST에는 새 UUID `Idempotency-Key`를 사용하고, 네트워크 재전송에만 같은 키·본문을 유지합니다. 요청 필드와 예시는 [API 명세](api.md)를 참고하세요.

## 기존 학생 로그인 테스트

이미 가입한 학생 계정으로 다시 로그인할 때 사용합니다.

1. `GET /api/v1/auth/session`으로 기존 세션이 유효한지 확인합니다. 200이면 그대로 담당 기능 테스트를 진행하고, 401이면 아래 절차로 로그인합니다.
2. 가입한 학교 이메일로 인증번호 발급·검증을 다시 진행해 새 `verificationToken`을 받습니다. 가입에 사용한 증표를 재사용하지 않습니다.
3. 새 UUID `Idempotency-Key`와 아래 본문으로 `POST /api/v1/auth/student-sessions`를 호출합니다. 성공하면 200과 세션·CSRF 토큰을 받습니다.

```json
{
  "verificationToken": "새로_발급받은_증표"
}
```

미가입 이메일은 먼저 위 가입 절차를 진행합니다. 로그인 요청의 네트워크 재전송에는 같은 키·증표를 유지합니다.

## 관계자 로그인 테스트

관계자 수요·리포트 기능 테스트에는 발급된 STAFF 계정을 사용합니다. 학생 프로필·학교 이메일 인증은 필요하지 않습니다. 로컬 DB에 계정이 없다면 아래 순서로 준비합니다.

1. 서버를 한 번 실행한 뒤, IntelliJ **Terminal** 또는 PowerShell에서 비밀번호 해시를 만듭니다. 비밀번호를 숨겨서 입력받고 BCrypt 해시를 출력합니다. 로그인에는 입력한 비밀번호 원문을 사용합니다. Gradle 의존성이 없다면 먼저 `.\gradlew.bat compileJava`를 실행합니다.

```powershell
$staffCryptoJar = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.springframework.security\spring-security-crypto" -Recurse -Filter 'spring-security-crypto-*.jar' |
    Where-Object { $_.Name -notmatch '-sources|-javadoc' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
java -cp $staffCryptoJar.FullName tools/StaffPasswordHash.java
```

2. 로컬 DB에 접속해 대학 ID를 조회합니다. DB 이름·사용자는 `.env`에서 변경했다면 맞춰 사용합니다.

```powershell
docker compose exec postgres psql -U owlly -d owlly
```

```sql
SELECT id, name FROM universities ORDER BY id;
```

3. 다음 SQL의 `실제_대학_ID`를 조회한 숫자로, `생성한_BCrypt_해시`를 위 명령의 출력으로 바꿉니다. 이메일은 고유한 소문자 주소를 사용하고 기존 학생 계정은 변경하지 않습니다.

```sql
INSERT INTO users (university_id, email, role, password_hash, status, created_at, updated_at)
VALUES (실제_대학_ID, 'staff-test@example.com', 'STAFF', '생성한_BCrypt_해시', 'ACTIVE', NOW(), NOW());
```

DB 접속은 `\q`로 종료합니다.

계정 준비 후 `POST /api/v1/auth/staff-sessions`에 새 UUID `Idempotency-Key`와 아래 본문을 보냅니다. 성공하면 200과 STAFF 계정 정보·대학명·CSRF 토큰을 받습니다.

```json
{
  "email": "staff-test@example.com",
  "password": "직접_설정한_비밀번호"
}
```

## 가입·로그인 후 공통 사용법

- localhost HTTP 개발은 `SESSION_COOKIE_SECURE=false`, 세션 기간은 `SESSION_TIMEOUT=30d`를 사용합니다. 관계자 로그인용 `STAFF_LOGIN_*` 변수는 기본값이 있어 필수 추가할 항목이 없습니다.
- 인증 POST에는 허용된 `Origin`과 UUID `Idempotency-Key`가 필요합니다. UUID는 PowerShell에서 `[guid]::NewGuid().ToString()`으로 생성합니다.
- 가입·로그인 후 같은 브라우저의 Swagger는 `owlly_session` 쿠키를 자동 전송합니다. 프런트 fetch는 `credentials: 'include'`, Axios는 `withCredentials: true`를 사용합니다. Postman/curl에서도 쿠키를 유지해야 합니다.
- 로그인된 POST/PUT/PATCH/DELETE에는 가입·로그인 응답 또는 `GET /api/v1/auth/session`의 `data.csrfToken`을 `X-CSRF-Token` 헤더로 보냅니다. 기존 인증 쿠키를 동반한 로그인 재시도에도 필요하며, 로그인 후에는 새 토큰을 사용합니다. Swagger에 헤더 입력란이 없으면 Postman/curl에서 전달합니다.
- 역할을 바꾸려면 새 UUID 키·현재 CSRF 토큰으로 `DELETE /api/v1/auth/session`을 호출해 로그아웃(204)한 뒤 다른 계정으로 로그인합니다. 또는 별도 시크릿 창을 사용합니다. 학생 기능은 STUDENT, 관계자 수요·리포트 기능은 STAFF 계정으로 호출합니다.
