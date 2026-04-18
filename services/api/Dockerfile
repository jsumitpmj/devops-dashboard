# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY services/pom.xml services/pom.xml
COPY services/shared/pom.xml services/shared/pom.xml
COPY services/api/pom.xml services/api/pom.xml
COPY services/auth/pom.xml services/auth/pom.xml
COPY services/deployment/pom.xml services/deployment/pom.xml
COPY services/notification/pom.xml services/notification/pom.xml
COPY services/worker/pom.xml services/worker/pom.xml

# Install Maven wrapper
RUN apk add --no-cache maven

# Download dependencies (cached layer)
WORKDIR /app/services
RUN mvn dependency:go-offline -pl shared,api -am -B -q || true

# Copy source
COPY services/shared/src /app/services/shared/src
COPY services/api/src /app/services/api/src

# Build
RUN mvn clean package -pl shared,api -am -DskipTests -B -q

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/api/target/*.jar app.jar
EXPOSE 4000
ENTRYPOINT ["java", "-jar", "app.jar"]
