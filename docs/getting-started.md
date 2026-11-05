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

- 현재 Security는 모든 요청을 `permitAll` 상태 — 인증 로직(JWT)은 도메인 작업 시 추가 예정.
- 테스트는 실제 PostgreSQL이 떠 있어야 통과(별도 테스트 DB 설정 전까지).
