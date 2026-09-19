FROM gradle:9.6.0-jdk21 AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin storyteller
COPY --from=build --chown=storyteller:storyteller /workspace/build/install/dairn-storyteller-telegram-lab /app

USER storyteller
ENTRYPOINT ["/app/bin/dairn-storyteller-telegram-lab"]
