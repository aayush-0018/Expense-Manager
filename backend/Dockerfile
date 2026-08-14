# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 — build the jar
#
# pom.xml is copied on its own first so the dependency download lands in its
# own layer: source edits then rebuild in seconds instead of re-resolving every
# dependency on each push.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests need a live PostgreSQL, which a build container does not have.
# They run in CI / locally instead — see README.
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 — runtime
#
# JRE rather than JDK, and only the jar is carried over, so the Maven cache and
# toolchain (~800 MB) never reach the deployed image.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Never run the app as root.
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/target/expense-manager-*.jar app.jar
USER app

# MaxRAMPercentage (not a fixed -Xmx) lets the heap follow whatever the
# container is actually given. SerialGC has the smallest footprint on the
# single small instance this runs on.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# exec form via sh so JAVA_OPTS expands, while the JVM still becomes PID 1
# and receives SIGTERM directly for a clean shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
