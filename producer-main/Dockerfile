FROM gradle:9.5.1-jdk26 AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew

RUN ./gradlew clean bootJar -x test

FROM eclipse-temurin:26-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-Xmx300m","-jar","app.jar"]