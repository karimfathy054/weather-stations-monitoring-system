import os
import json
import time
import pandas as pd
import pyarrow.parquet as pq
from elasticsearch import Elasticsearch, helpers
from datetime import datetime
import logging
from typing import Dict, List, Set, Tuple

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("parquet_processor.log"),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("ParquetProcessor")

class ParquetProcessor:
    def __init__(self, 
                 parquet_dir: str, 
                 processed_files_path: str, 
                 elastic_host: str = "localhost",
                 elastic_port: int = 9200,
                 elastic_index: str = "weather_stations_metrics",
                 scan_interval: int = 300):
        """
        Initialize the Parquet processor.
        
        Args:
            parquet_dir: Directory containing parquet files
            processed_files_path: Path to store processed files list
            elastic_host: Elasticsearch host
            elastic_port: Elasticsearch port
            elastic_index: Elasticsearch index name
            scan_interval: Time between directory scans in seconds
        """
        self.parquet_dir = parquet_dir
        self.processed_files_path = processed_files_path
        self.elastic_host = elastic_host
        self.elastic_port = elastic_port
        self.elastic_index = elastic_index
        self.scan_interval = scan_interval
        
        # Load or initialize set of processed files
        self.processed_files = self._load_processed_files()
        
        # Connect to Elasticsearch
        self.es = Elasticsearch([f"http://{elastic_host}:{elastic_port}"])
        self._setup_elasticsearch()
        
        # Dict to track station metrics
        self.station_metrics: Dict[str, Dict] = {}

    def _load_processed_files(self) -> Set[str]:
        """Load the list of already processed files"""
        if os.path.exists(self.processed_files_path):
            with open(self.processed_files_path, 'r') as f:
                return set(line.strip() for line in f)
        return set()

    def _save_processed_files(self) -> None:
        """Save the set of processed files to disk"""
        with open(self.processed_files_path, 'w') as f:
            for file_path in self.processed_files:
                f.write(f"{file_path}\n")

    def _setup_elasticsearch(self) -> None:
        """Setup Elasticsearch index with appropriate mappings"""
        if not self.es.indices.exists(index=self.elastic_index):
            mapping = {
                "mappings": {
                    "properties": {
                        "station_id": {"type": "keyword"},
                        "timestamp": {"type": "date"},
                        "message_count": {"type": "integer"},
                        "dropped_messages": {"type": "integer"},
                        "dropped_percentage": {"type": "float"},
                        "battery_status_high": {"type": "float"},
                        "battery_status_medium": {"type": "float"},
                        "battery_status_low": {"type": "float"},
                        "min_sno": {"type": "long"},
                        "max_sno": {"type": "long"}
                    }
                }
            }
            self.es.indices.create(index=self.elastic_index, body=mapping)
            logger.info(f"Created Elasticsearch index: {self.elastic_index}")

    def _find_parquet_files(self) -> List[str]:
        """Find all parquet files in the directory structure"""
        all_files = []
        for root, _, files in os.walk(self.parquet_dir):
            for file in files:
                if file.endswith('.parquet'):
                    all_files.append(os.path.join(root, file))
        return all_files

    def _get_station_id_from_path(self, file_path: str) -> str:
        """Extract station ID from file path"""
        # Path format is expected to be: output_dir/station_id/yyyy_MM_dd/part-XXXXXX.parquet
        parts = file_path.split(os.sep)
        if len(parts) >= 2:
            return parts[-3]  # Station ID should be the third-last component
        return "unknown"

    def _process_file(self, file_path: str) -> None:
        """Process a single parquet file and update metrics"""
        try:
            logger.info(f"Processing file: {file_path}")
            
            # Read parquet file
            table = pq.read_table(file_path)
            df = table.to_pandas()
            
            # Extract station ID from path
            station_id = self._get_station_id_from_path(file_path)
            
            # Initialize metrics for this station if not exists
            if station_id not in self.station_metrics:
                self.station_metrics[station_id] = {
                    "min_sno": float('inf'),
                    "max_sno": -float('inf'),
                    "message_count": 0,
                    "battery_status_counts": {"high": 0, "medium": 0, "low": 0}
                }
            
            # Update metrics
            metrics = self.station_metrics[station_id]
            
            # Track sequence numbers for gap detection
            min_sno = df['s_no'].min()
            max_sno = df['s_no'].max()
            metrics["min_sno"] = min(metrics["min_sno"], min_sno)
            metrics["max_sno"] = max(metrics["max_sno"], max_sno)
            
            # Count messages
            count = len(df)
            metrics["message_count"] += count
            
            # Track battery status
            status_counts = df['battery_status'].value_counts()
            for status in ["high", "medium", "low"]:
                if status in status_counts:
                    metrics["battery_status_counts"][status] += status_counts[status]
            
            # Mark as processed
            self.processed_files.add(file_path)
            
        except Exception as e:
            logger.error(f"Error processing file {file_path}: {str(e)}")

    def _calculate_metrics_and_send_to_es(self) -> None:
        """Calculate final metrics and send to Elasticsearch"""
        bulk_data = []
        
        for station_id, metrics in self.station_metrics.items():
            # Calculate dropped messages
            expected_messages = metrics["max_sno"] - metrics["min_sno"] + 1
            dropped_messages = expected_messages - metrics["message_count"]
            dropped_percentage = (dropped_messages / expected_messages) * 100 if expected_messages > 0 else 0
            
            # Calculate battery status percentages
            total_battery_statuses = sum(metrics["battery_status_counts"].values())
            battery_high_pct = (metrics["battery_status_counts"]["high"] / total_battery_statuses * 100) if total_battery_statuses > 0 else 0
            battery_medium_pct = (metrics["battery_status_counts"]["medium"] / total_battery_statuses * 100) if total_battery_statuses > 0 else 0
            battery_low_pct = (metrics["battery_status_counts"]["low"] / total_battery_statuses * 100) if total_battery_statuses > 0 else 0
            
            # Create document for Elasticsearch
            doc = {
                "station_id": station_id,
                "timestamp": datetime.now().isoformat(),
                "message_count": metrics["message_count"],
                "dropped_messages": int(dropped_messages),
                "dropped_percentage": round(dropped_percentage, 2),
                "battery_status_high": round(battery_high_pct, 2),
                "battery_status_medium": round(battery_medium_pct, 2),
                "battery_status_low": round(battery_low_pct, 2),
                "min_sno": int(metrics["min_sno"]),
                "max_sno": int(metrics["max_sno"])
            }
            
            # Add to bulk data
            bulk_data.append({
                "_index": self.elastic_index,
                "_source": doc
            })
            
            logger.info(f"Station {station_id}: Processed {metrics['message_count']} messages, "
                       f"Dropped: {dropped_percentage:.2f}%, "
                       f"Battery status - High: {battery_high_pct:.2f}%, Medium: {battery_medium_pct:.2f}%, Low: {battery_low_pct:.2f}%")
        
        # Send to Elasticsearch
        if bulk_data:
            helpers.bulk(self.es, bulk_data)
            logger.info(f"Sent {len(bulk_data)} documents to Elasticsearch")

    def run_once(self) -> None:
        """Run a single processing cycle"""
        try:
            # Find all parquet files
            all_files = self._find_parquet_files()
            new_files = [f for f in all_files if f not in self.processed_files]
            
            if not new_files:
                logger.info("No new files to process")
                return
            
            logger.info(f"Found {len(new_files)} new files to process")
            
            # Process new files
            for file_path in new_files:
                self._process_file(file_path)
            
            # Calculate and send metrics to Elasticsearch
            self._calculate_metrics_and_send_to_es()
            
            # Save the updated list of processed files
            self._save_processed_files()
            
        except Exception as e:
            logger.error(f"Error in processing cycle: {str(e)}")

    def run_forever(self) -> None:
        """Run the processor in a continuous loop"""
        logger.info("Starting continuous processing loop")
        try:
            while True:
                self.run_once()
                logger.info(f"Sleeping for {self.scan_interval} seconds")
                time.sleep(self.scan_interval)
        except KeyboardInterrupt:
            logger.info("Processor stopped by user")
        finally:
            self._save_processed_files()

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="Process Parquet files and send metrics to Elasticsearch")
    parser.add_argument("--parquet-dir", default=os.environ.get("PARQUET_DIR", "/opt/weather-data"),
                        help="Directory containing Parquet files")
    parser.add_argument("--processed-files", default=os.environ.get("PROCESSED_FILES", "processed_files.txt"),
                        help="File to store list of processed files")
    parser.add_argument("--elastic-host", default=os.environ.get("ELASTIC_HOST", "localhost"),
                        help="Elasticsearch host")
    parser.add_argument("--elastic-port", type=int, default=int(os.environ.get("ELASTIC_PORT", "9200")),
                        help="Elasticsearch port")
    parser.add_argument("--elastic-index", default=os.environ.get("ELASTIC_INDEX", "weather_stations_metrics"),
                        help="Elasticsearch index name")
    parser.add_argument("--scan-interval", type=int, default=int(os.environ.get("SCAN_INTERVAL", "300")),
                        help="Time between scans in seconds")
    parser.add_argument("--run-once", action="store_true", help="Run once and exit")
    
    args = parser.parse_args()
    
    processor = ParquetProcessor(
        parquet_dir=args.parquet_dir,
        processed_files_path=args.processed_files,
        elastic_host=args.elastic_host,
        elastic_port=args.elastic_port,
        elastic_index=args.elastic_index,
        scan_interval=args.scan_interval
    )
    
    if args.run_once:
        processor.run_once()
    else:
        processor.run_forever()