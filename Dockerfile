FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src

RUN ./gradlew clean bootJar --no-daemon

EXPOSE 8080

CMD ["java", "-jar", "build/libs/planit-backend-0.0.1-SNAPSHOT.jar"]
