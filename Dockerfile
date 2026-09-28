# Multi-stage build for Library Management System
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first (better layer caching)
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build runnable JAR
COPY backend/src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Copy built artifact
COPY --from=build /app/target/*.jar /app/app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R app:app /app

USER app

EXPOSE 8080

# MaxRAMPercentage sizes the heap from the OCI compute shape / container memory
# limit instead of a hard-coded -Xmx. Override JAVA_OPTS per instance in .env.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# start-period covers a cold start on a small compute shape: Spring context
# init plus the Flyway run on first boot can exceed 60s. Do not shorten it
# below the observed startup time or Docker will mark a healthy container
# unhealthy during a legitimate boot.
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# exec keeps java as PID 1 so SIGTERM from `docker stop` reaches the JVM.
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
