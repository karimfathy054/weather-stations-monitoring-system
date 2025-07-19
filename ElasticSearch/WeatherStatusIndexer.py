import os
import pandas as pd
import json
from elasticsearch import Elasticsearch, helpers
from datetime import datetime

class WeatherStatusIndexer:
    def __init__(self, root_data_dir, index_name="weather_statuses", es_host="http://localhost:9200"):
        self.root_data_dir = root_data_dir
        self.index_name = index_name
        self.es = Elasticsearch(es_host)

    def flatten_and_tag(self, df, station_id):
        weather_df = pd.json_normalize(df['weather'])
        df = pd.concat([df.drop(columns=['weather']), weather_df], axis=1)

        df['status_timestamp'] = pd.to_datetime(df['status_timestamp'], unit='s')
        df['station_id'] = int(station_id)

        # Sort to detect dropped messages
        df = df.sort_values(by='s_no')
        df['dropped'] = df['s_no'].diff().fillna(1).astype(int) > 1  
        return df

    def index_exists_or_create(self):
        if self.es.indices.exists(index=self.index_name):
            print(f"Index '{self.index_name}' already exists. Deleting and recreating...")
            self.es.indices.delete(index=self.index_name)

        mapping = {
            "mappings": {
                "properties": {
                    "station_id": {"type": "long"},
                    "s_no": {"type": "long"},
                    "battery_status": {"type": "keyword"},
                    "status_timestamp": {"type": "date"},
                    "humidity": {"type": "integer"},
                    "temperature": {"type": "integer"},
                    "wind_speed": {"type": "integer"},
                    "dropped": {"type": "boolean"}
                }
            }
        }

        self.es.indices.create(index=self.index_name, body=mapping)
        print("Index created.")

    def process_all_stations(self):
        all_docs = []

        for station_folder in os.listdir(self.root_data_dir):
            folder_path = os.path.join(self.root_data_dir, station_folder)
            if not os.path.isdir(folder_path):
                continue

            for ts in os.listdir(folder_path):
                time_folder = os.path.join(folder_path, ts)
                
                try:
                    station_id = int(station_folder)
                except:
                    print(f"Skipping folder {station_folder}, name format not recognized.")
                    continue

                for file in os.listdir(time_folder):
                    if file.endswith(".parquet"):
                        file_path = os.path.join(time_folder, file)
                        print(f"Processing {file_path}...")
                        df = pd.read_parquet(file_path)
                        df = self.flatten_and_tag(df, station_id)
                        all_docs.extend(df.to_dict(orient="records"))

        print(f"Total documents to index: {len(all_docs)}")
        self.bulk_upload(all_docs)

    def bulk_upload(self, docs):
        print("Uploading to Elasticsearch...")
        actions = [
            {
                "_index": self.index_name,
                "_source": doc
            }
            for doc in docs
        ]
        helpers.bulk(self.es, actions)
        print("Upload complete.")

    def run(self):
        self.index_exists_or_create()
        self.process_all_stations()


if __name__ == "__main__":
    indexer = WeatherStatusIndexer(root_data_dir="/home/karim/parquet")
    indexer.run()
