FROM gradle:8.9-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY gradlew ./
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY scraper/build.gradle.kts scraper/build.gradle.kts
COPY shared/src shared/src
COPY scraper/src scraper/src
RUN gradle :scraper:installDist --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/scraper/build/install/scraper ./
ENTRYPOINT ["./bin/scraper"]