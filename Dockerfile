# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# 의존성 캐시를 위해 래퍼/빌드 스크립트 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 bootJar 생성 (테스트는 CI에서 별도 수행)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 비루트 사용자로 실행
RUN groupadd --system owlly && useradd --system --gid owlly owlly

COPY --from=builder /workspace/build/libs/*-SNAPSHOT.jar app.jar
RUN chown owlly:owlly app.jar

USER owlly
EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
