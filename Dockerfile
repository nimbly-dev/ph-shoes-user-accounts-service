# ---------- Build stage ----------
FROM maven:3-amazoncorretto-21 AS build
WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -e -U -DskipTests dependency:go-offline

# Build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

# ---------- Runtime stage ----------
FROM amazoncorretto:21-alpine AS runtime
ENV APP_HOME=/app \
    JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:MaxRAMPercentage=75" \
    SPRING_PROFILES_ACTIVE=prod \
    PORT=8082

# Add a non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Healthcheck needs curl on Alpine
RUN apk add --no-cache curl

USER spring:spring
WORKDIR ${APP_HOME}

# Copy fat JAR
COPY --from=build /workspace/target/*.jar app.jar

# Expose the runtime port (purely documentation for many platforms)
EXPOSE ${PORT}

# Healthcheck (adjust if your actuator path/port differs)
#HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
#  CMD curl -fsS http://127.0.0.1:${PORT}/actuator/health | grep '"status":"UP"' || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
