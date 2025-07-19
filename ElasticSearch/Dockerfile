# Dockerfile for ElasticSearch with WeatherStatusIndexer
FROM python:3.9-slim

WORKDIR /app

# Install required Python packages
COPY requirements.txt /app/
RUN pip install --no-cache-dir -r requirements.txt

# Copy the WeatherStatusIndexer script
COPY WeatherStatusIndexer.py /app/

# Set the entrypoint to run the WeatherStatusIndexer
ENTRYPOINT ["python", "/app/WeatherStatusIndexer.py"]
