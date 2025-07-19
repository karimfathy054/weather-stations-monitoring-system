# Dockerfile for Rain Detector
FROM openjdk:11-jre-slim

WORKDIR /app

# Copy the compiled JAR file
COPY target/raining_detector-1.0-SNAPSHOT-jar-with-dependencies.jar /app/rain-detector.jar

# Set the entrypoint to run the Rain Detector
ENTRYPOINT ["java", "-jar", "/app/rain-detector.jar"]
