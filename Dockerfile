# Multi-stage build for Library Management System
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first (better layer caching)
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY backend/src src
RUN ./mvnw package -DskipTests -B && \
    mkdir -p target/dependency && \
    cd target/dependency && \
    jar xf ../library-management-system-*.jar

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Copy built artifacts from build stage
COPY --from=build /app/target/dependency/BOOT-INF/classes ./classes
COPY --from=build /app/target/dependency/BOOT-INF/lib ./lib
COPY --from=build /app/target/dependency/META-INF ./META-INF

# Create logs directory
RUN mkdir -p /app/logs && chown -R app:app /app

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-cp", "classes:lib/*", "com.example.lms.LibraryManagementApplication"]
