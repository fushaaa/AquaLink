FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/aqualink-0.0.1-SNAPSHOT.jar /app/aqualink.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/aqualink.jar"]