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
- 대학·프로필 선택값·공개 인증·Swagger·health 이외의 경로는 인증이 필요합니다. 학생 가입·자동 로그인 로직은 구현했지만 공통 행정동 판별 서비스 연결 전에는 가입이 503으로 보류됩니다. 기존 학생 재로그인은 후속 구현입니다.
- `SessionIntegrationTest`는 H2 기반 JDBC 세션 통합 테스트입니다. 기존 `OwllyApplicationTests.contextLoads`는 실제 PostgreSQL과 `.env` 설정이 필요합니다.

## 학생 가입 테스트

**현재 API-02는 완료, API-07은 공통 행정동 판별 서비스 연결 대기입니다.** 실제 구현이 없으면 앱은 기동되지만 올바른 가입 요청에도 `503 POLICY_NOT_CONFIGURED`를 반환합니다. 테스트에서는 연결 지점만 mock으로 대체합니다.

1. 공통 지역 서비스 담당과 실제 메서드·응답을 맞춘 뒤 `DropoffLocationService` 구현 Bean을 추가해 기존 공통 서비스를 위임 호출합니다. 카카오 호출과 기준정보 매핑을 가입 브랜치에서 중복 구현하지 않습니다.
2. 구현은 `resolveDong(latitude, longitude)`에서 DB에 존재하는 `AdministrativeArea`(ID 필수, level=DONG)를 반환해야 합니다. 이 계약은 가입 측에서 제안한 연결 규격이며 공통 서비스가 이미 이 인터페이스를 구현했다는 뜻이 아닙니다. 외부 API 키·DB 기준정보 설정은 실제 공통 구현의 안내를 따릅니다.
3. Swagger에서 대학 조회 → 이메일 발급(202) → 코드 검증(200) → 증표로 가입(201)을 진행합니다. 각 명령에 서로 다른 UUID `Idempotency-Key`를 사용합니다. 가입 재시도만 같은 키와 본문을 유지합니다.
4. `GET /api/v1/profile-options`의 코드 `FEMALE`/`MALE`를 사용합니다. 이름은 2~20자이며 `sameGenderDefault`는 `true`/`false` 모두 허용합니다. 선택한 값을 저장하고, 생략/null이면 true로 처리합니다. 선택값 조회 응답의 true는 초기 기본값입니다.
5. 가입 응답의 `data.csrfToken`과 `owlly_session` 쿠키로 세션 조회·보호 API·로그아웃을 확인합니다. Swagger에 CSRF 헤더 입력란이 없는 경우 Postman/curl에서 `X-CSRF-Token`과 쿠키를 전달합니다. 로그아웃 후 세션 조회는 401입니다.

가입과 현재 세션 조회 응답의 `data.user`에 `universityName`을 추가했습니다. 하차 동 이름을 반환하는 프로필 조회·수정 API는 후속 브랜치에서 구현합니다.

`StudentRegistrationIntegrationTest`는 실제 PostgreSQL을 사용하며 공통 행정동 판별 계약만 mock으로 대체합니다. 테스트별 무작위 이메일·행정동을 만들고 종료 시 그 데이터와 세션만 정리합니다. 미연결 시 503·증표 소비 롤백·계정/세션 미생성을 검증합니다. 성공 시나리오 테스트 통과는 실제 공통 연동 완료를 뜻하지 않습니다. `.env`에 실제 DB/암호화 키 설정이 필요하고, 주석은 `//`가 아닌 `#`로 작성합니다.
