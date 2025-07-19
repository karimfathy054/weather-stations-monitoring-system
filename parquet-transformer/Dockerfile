# Dockerfile for Parquet Transformer
FROM openjdk:11-jre-slim

WORKDIR /app

# Copy the compiled classes and resources
COPY target/classes /app/classes
COPY parqFiles /app/parqFiles

# Set the entrypoint to run the Parquet Transformer
ENTRYPOINT ["java", "-cp", "/app/classes", "com.example.Main"]
