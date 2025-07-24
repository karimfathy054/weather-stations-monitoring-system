# Weather-Stations-Monitoring-System

This project implements a scalable, cloud-native **weather monitoring system** using distributed weather stations, Apache Kafka for data ingestion, Parquet files for archival, a custom **Bitcask key-value store** for fast retrieval of the latest station readings, and Elasticsearch/Kibana for analytics and visualization.

Deployed entirely on **Kubernetes**, this system simulates real-world IoT sensor streaming at scale and highlights modern data-intensive application patterns.

---

## 📦 Project Structure

### 1. Weather Stations (IoT Simulators)
- Simulate 10 weather stations, each emitting a JSON weather reading every second.
- Emissions include temperature, humidity, wind speed, and battery level.
- Battery levels (`low`, `medium`, `high`) follow a specific distribution (30/40/30), and 10% of messages are randomly dropped.
- Messages are pushed to Kafka topics.

### 2. Kafka Pipeline
- Uses **Apache Kafka** for ingestion of messages from weather stations.
- Kafka Streams API is used to:
  - Detect rain (humidity > 70%) and forward special alerts to a separate topic.

### 3. Central Base Station
A core component that:
- **Consumes** Kafka messages from all stations.
- **Stores** all historical data in **Parquet files**, partitioned by station ID and timestamp.
- Maintains a **custom Bitcask key-value store** holding the **latest weather reading per station**.

### 4. Bitcask Store
Custom implementation of the **Bitcask** storage engine:
- Efficient write-ahead log-based key-value store.
- Includes **hint files** for fast startup and recovery.
- Scheduled **compaction** avoids read disruption.
- Offers a client interface for:
  - Viewing all current station statuses.
  - Querying specific keys.
  - Stress testing with concurrent clients.

### 5. Elasticsearch & Kibana
- Parquet files are used as a data source.
- Indexed in **Elasticsearch**, visualized in **Kibana**.
- Queries include:
  - Battery status distribution per station.
  - Dropped message rates (should confirm the expected 10%).

---

## 🚀 Kubernetes Deployment

The entire system is containerized and deployed using **Kubernetes**, ensuring modularity and scalability.

### ⚙️ Deployed Components:
- 10 Weather Station Pods
- Kafka + Zookeeper Pods (Bitnami images)
- Central Station Pod
- Elasticsearch + Kibana Pods
- Shared Persistent Volumes:
  - For Parquet archive storage.
  - For Bitcask LSM storage.

### ✅ Features:
- Fully reproducible with Dockerfiles and K8s YAMLs.
- Horizontal scalability via deployment configuration.
- Central server profiling using **Java Flight Recorder (JFR)** to analyze memory, GC, and I/O.

### 📊 Sample Visualizations
visualizations are created using kibana 
- Battery statuses
<img width="789" height="368" alt="image" src="https://github.com/user-attachments/assets/d6fdc59f-a2b6-4039-9466-ef82687e81c4" />

- Dropped messages
<img width="791" height="358" alt="image" src="https://github.com/user-attachments/assets/8dca9c37-46ae-49ac-937a-a2b107086a01" />

### 🧩 Enterprise Integration Patterns Used
This project follows several key Enterprise Integration Patterns to ensure modularity, robustness, and scalability:

1. Channel Adapter
A message normalizer module in the central station enables the Bitcask server to poll messages from a Kafka topic and save the latest values in its file system.

2. Invalid Message Channel
If a message has a malformed schema or invalid JSON, it is redirected from the main processing flow to a dedicated invalid-message topic for isolation and logging.

3. Content Enricher
A Kubernetes cron job module enriches archived Parquet files by adding computed fields for analytics (e.g., dropped message percentages).

4. Message Normalizer
The module sending messages to Bitcask acts as a normalizer, allowing for extension to support multiple station schemas while maintaining a consistent internal format.

5. Content Filter
The rain-detector module produces filtered, concise messages when humidity exceeds a threshold (e.g.,
"Rain detected at station ID: 1 at humidity level: 76"), allowing downstream modules to act on these alerts.

6. Polling Consumer
Kafka consumers are implemented using the Kafka Consumer API, which continuously polls for new messages and processes them in real-time.

7. Durable Subscriber
The Elasticsearch pod acts as a durable subscriber to weather data summaries written in Parquet format, persisting and indexing even if the consumer was temporarily offline.

---

for JFR profiling and more details please refere to the [project report](docs/DDIA_Project_report.pdf) and [assignment pdf](docs/Project_assignment.pdf)
