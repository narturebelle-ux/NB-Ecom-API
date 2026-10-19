# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
# Separate layer so dependency downloads are cached across builds unless build.gradle changes.
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Cloud Run injects PORT; application-prod.yml already binds server.port to it.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
