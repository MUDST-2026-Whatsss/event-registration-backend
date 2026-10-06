# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre

RUN groupadd --system eventapp \
    && useradd --system --gid eventapp --no-create-home eventapp

WORKDIR /app

COPY --from=build --chown=eventapp:eventapp \
    /workspace/target/event-registration-0.0.1-SNAPSHOT.jar /app/app.jar

USER eventapp

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
