# ==========================================
# Stage 1: Build Application with Maven
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper files and pom.xml first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build artifact
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Stage 2: Lean Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: Run as a non-privileged user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy executable JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Render injects dynamic $PORT (defaults to 8080 locally)
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]