# owlly

Spring Boot 3.5.3 / Java 21 백엔드 프로젝트.

## 기술 스택

- Spring Boot 3.5.3 (Web, Validation, Data JPA, Security, Actuator)
- Java 21
- PostgreSQL 16
- Spring Security + Spring Session JDBC (PostgreSQL 서버 세션)
- Swagger (springdoc-openapi)
- spring-dotenv (`.env` 로딩)

## Quick Start

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

→ 서버: http://localhost:8080

## 문서

- [개발 환경 세팅](docs/getting-started.md)
- [API 명세](docs/api.md)
- [ERD](docs/erd.md)
