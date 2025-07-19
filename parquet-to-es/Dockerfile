FROM python:3.12-slim

# Set working directory
WORKDIR /app

# Copy the application code
COPY src/parquet_to_elastic.py /app/
COPY requirements.txt /app/

# Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Create the directory for weather data
RUN mkdir -p /app/weather-data /app/out

# Set environment variables with default values
ENV PARQUET_DIR=/app/weather-data
ENV PROCESSED_FILES=/app/out/processed_files.txt
ENV ELASTIC_HOST=localhost
ENV ELASTIC_PORT=9200
ENV ELASTIC_INDEX=weather_stations_metrics
ENV SCAN_INTERVAL=300

# Set the default command to run the script
CMD ["python", "parquet_to_elastic.py", "--run-once"]
