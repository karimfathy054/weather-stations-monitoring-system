#!/bin/bash
# Script to build and deploy the parquet-to-csv converter

echo "Building Parquet to CSV converter Docker image..."
cd /home/karim/weather-stations-monitoring-system/parquet-to-csv
docker build -t parquet-to-csv:latest .

echo "Deploying to Kubernetes..."
kubectl apply -f /home/karim/weather-stations-monitoring-system/k8s/parquet-to-csv.yaml

echo "Done! You can check the status with:"
echo "kubectl get pods | grep parquet-to-csv"
