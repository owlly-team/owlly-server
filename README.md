# owlly

Spring Boot 3.5.3 / Java 21 백엔드 프로젝트.

## 기술 스택

- Spring Boot 3.5.3 (Web, Validation, Data JPA, Security, Actuator)
- Java 21
- PostgreSQL
- JWT (jjwt 0.13.0)
- Swagger (springdoc-openapi)
- spring-dotenv (`.env` 로딩)

## 사전 준비

- JDK 21
- Docker (로컬 DB 실행용)

## 1. 로컬 DB(PostgreSQL) 띄우기

```bash
docker run -d \
  --name owlly-db \
  -e POSTGRES_DB=owlly \
  -e POSTGRES_PASSWORD=비밀번호 \
  -p 5432:5432 \
  postgres:16
```

컨테이너 관리:

```bash
docker start owlly-db   # 재시작
docker stop owlly-db    # 중지
docker rm -f owlly-db   # 삭제 (데이터도 같이 날아감)
```

## 2. 환경변수(.env) 설정

프로젝트 루트에 `.env` 파일 생성:

```env
DB_USERNAME=postgres
DB_PASSWORD=비밀번호
# 아래는 기본값이 있어 로컬 기본 설정이면 생략 가능
# DB_HOST=localhost
# DB_PORT=5432
# DB_NAME=owlly
```

> `.env`는 커밋 금지 — `.gitignore`에 포함되어 있는지 확인하세요.

## 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

빌드 결과물(jar)로 실행하려면:

```bash
./gradlew build -x test
java -jar build/libs/owlly-0.0.1-SNAPSHOT.jar
```

## 접속 확인

- API 서버: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

## 참고

- 현재 Security는 모든 요청을 `permitAll` 상태 — 인증 로직(JWT)은 도메인 작업 시 추가 예정.
- 테스트는 실제 PostgreSQL이 떠 있어야 통과(별도 테스트 DB 설정 전까지).
