# Build stage
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 springuser
COPY --from=build /app/target/spring-boot-efk-0.0.1-SNAPSHOT.jar app.jar

USER 1001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
