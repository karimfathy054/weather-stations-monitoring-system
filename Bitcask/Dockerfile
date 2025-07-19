# Use a lightweight Java runtime
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the pre-built JAR file
COPY target/Bitcask-1.0-SNAPSHOT.jar /app/bitcask.jar

# Copy properties file to the correct location
COPY src/main/resources/system.properties /app/system.properties

# Create directories for logs and hints
RUN mkdir -p /data/logs /data/hints

# Expose the port defined in the properties file (5000)
EXPOSE 5000

# Set the entry point
CMD ["java", "-jar", "bitcask.jar"]