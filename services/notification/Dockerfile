FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY services/pom.xml services/pom.xml
COPY services/shared/pom.xml services/shared/pom.xml
COPY services/api/pom.xml services/api/pom.xml
COPY services/auth/pom.xml services/auth/pom.xml
COPY services/deployment/pom.xml services/deployment/pom.xml
COPY services/notification/pom.xml services/notification/pom.xml
COPY services/worker/pom.xml services/worker/pom.xml
RUN apk add --no-cache maven
WORKDIR /app/services
RUN mvn dependency:go-offline -pl shared,notification -am -B -q || true
COPY services/shared/src /app/services/shared/src
COPY services/notification/src /app/services/notification/src
RUN mvn clean package -pl shared,notification -am -Dmaven.test.skip=true -B -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/notification/target/*.jar app.jar
EXPOSE 4003
ENTRYPOINT ["java", "-jar", "app.jar"]
