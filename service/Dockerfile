# Build: Maven im Container, mvn verify führt die Tests aus (rote Tests = kein Image)
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Abhängigkeiten zuerst, damit sie bei reinen Code-Änderungen im Layer-Cache bleiben
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B verify

# Runtime: schlankes JRE, Non-Root-User
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /build/target/service.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
