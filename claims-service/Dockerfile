# syntax=docker/dockerfile:1.7
# claims-service: build with the JDK, run on the JRE as a non-root user, using Spring Boot's layered jar so a
# code change only rebuilds the small "application" layer instead of re-shipping every dependency.

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Dependencies first: this layer is cached until pom.xml changes.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
 && java -Djarmode=tools -jar target/claims-service-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre-noble
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
# Numeric UID so orchestrators (Kubernetes runAsNonRoot, ECS) can verify the container isn't root.
USER 10001:10001
EXPOSE 8080
# Size the heap from the container memory limit; crash fast (and get restarted) rather than limp on after OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=json-logs
# Shell form on purpose: Docker treats exit code 1 as unhealthy, and `curl -f`/wget exit with other codes.
# hadolint ignore=DL3025
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
