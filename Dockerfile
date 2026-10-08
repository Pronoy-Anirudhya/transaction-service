# syntax=docker/dockerfile:1

# ---- Build stage ----------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first: this layer (Gradle distribution + dependencies)
# is reused until one of these files changes.
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon > /dev/null

# Contracts are packaged onto the classpath (GET /openapi.yaml) by processResources.
COPY openapi/ openapi/
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:25-jre

# curl is used by the Compose healthcheck (/actuator/health/readiness); the JRE image lacks it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app
EXPOSE 8080

# P16: ZGC (generational is the only ZGC mode in JDK 25, so no -XX:+ZGenerational), heap fixed
# at 75% of the container memory limit (Initial = Max, i.e. -Xms = -Xmx, container aware),
# pre-touched; exit on OOM so the orchestrator restarts the instance.
ENV JAVA_OPTS="-XX:+UseZGC -XX:InitialRAMPercentage=75 -XX:MaxRAMPercentage=75 -XX:+AlwaysPreTouch -XX:+ExitOnOutOfMemoryError"

# exec replaces the shell, so SIGTERM reaches the JVM (graceful shutdown).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
