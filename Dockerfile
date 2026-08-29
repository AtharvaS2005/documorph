# ---- Build stage ----
FROM eclipse-temurin:23-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
COPY gradle-cache/wrapper/dists /root/.gradle/wrapper/dists
RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /app/build/libs/documorph-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]