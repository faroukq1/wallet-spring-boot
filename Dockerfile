# ---- build stage ----
FROM gradle:9.7.1-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon -x test \
    && find build/libs -name '*plain*.jar' -delete

# ---- run stage ----
FROM eclipse-temurin:21-jre-alpine AS run
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
