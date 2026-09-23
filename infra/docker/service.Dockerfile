# syntax=docker/dockerfile:1
#
# Shared build for every Spring Boot service in this monorepo — pass which one as a build arg, e.g.
# `--build-arg MODULE=services/account-service`. Every service needs its sibling `common` module's
# sources to compile (it's a reactor module, not a published artifact), so the build stage copies the
# whole repo in and lets Maven build just that module plus its dependencies (`-am`). The runtime stage
# then keeps only the one fat jar that module produced, on a slim JRE — the Maven/JDK toolchain never
# ships.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common ./common
COPY services ./services
ARG MODULE
# All 9 services share this same dependency tree (Spring Boot, the common module, ...); a cache mount
# means the other 8 don't each re-download it from scratch on a clean `docker compose build`.
RUN --mount=type=cache,target=/root/.m2 mvn -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine
# curl backs this image's own HEALTHCHECK (see docker-compose.yml) against the service's actuator endpoint.
RUN apk add --no-cache curl
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
