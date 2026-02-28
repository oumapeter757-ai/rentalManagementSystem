# ── Build stage ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven wrapper and pom first (cache dependencies)
COPY mvnw mvnw
COPY .mvn .mvn
COPY pom.xml pom.xml

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the JAR (skip tests — they ran in CI)
RUN ./mvnw package -DskipTests -B

# ── Runtime stage ─────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create uploads directory
RUN mkdir -p /app/uploads

# Copy the fat JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Render sets PORT env var dynamically
EXPOSE 8080

# Run with prod profile
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
