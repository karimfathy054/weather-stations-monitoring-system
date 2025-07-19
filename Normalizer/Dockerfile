# Dockerfile for Normalizer
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY target/Normalizer-1.0-SNAPSHOT.jar /app/normalizer.jar

ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9092
ENV KAFKA_TOPIC=weather-station
ENV BITCASK_SERVER_HOST=bitcask
ENV BITCASK_SERVER_PORT=5000


CMD ["java", "-jar", "normalizer.jar"]
