# syntax=docker/dockerfile:1

# ---- Build stage --------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Gradle wrapper + build scripts first so dependency resolution is cached
# in its own layer and isn't invalidated by source-only changes.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test
RUN cp build/libs/*.jar application.jar

# Split the boot jar into layers (deps / loader / snapshot-deps / app) so the
# runtime image below only re-copies the small "application" layer on rebuilds.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080

# DB接続情報・ANTHROPIC_API_KEYなどの秘密情報はイメージに焼き込まず、
# 実行時の環境変数(docker run -e / オーケストレータのSecrets機構など)で注入する。
ENTRYPOINT ["java", "-jar", "application.jar"]
