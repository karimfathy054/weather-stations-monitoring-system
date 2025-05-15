package com.example;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.avro.Schema;
import org.apache.avro.reflect.ReflectData;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {
    public static void main(String[] args) {
        
        Properties  props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"); 
        props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString()); 
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        
        Consumer<String,String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("weather-station"));

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayList<WeatherStationMsg> messages = new ArrayList<>();
        System.out.println("Starting consumer...");
        while(true){
            ConsumerRecords<String,String> jsonMsgs =  consumer.poll(Duration.ofMillis(1000));
            for ( ConsumerRecord<String,String> rec : jsonMsgs) {
                try {
                    // Deserialize the JSON string into a WeatherStationMsg object
                    WeatherStationMsg msg = objectMapper.readValue(rec.value(), WeatherStationMsg.class);
                    messages.add(msg);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
            if (messages.size() > 10) {
                try {
                    writeParquet(messages);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                messages.clear();
                break;
            }
        }
        

        // consumer.close();
    }
   public static void writeParquet(List<WeatherStationMsg> batch) throws IOException {
    System.out.println("Writing to Parquet...");
    Map<String, List<WeatherStationMsg>> grouped = batch.stream()
        .collect(Collectors.groupingBy(
            s -> "station_id=" + s.station_id
        ));

    for (Map.Entry<String, List<WeatherStationMsg>> entry : grouped.entrySet()) {
        String partitionPath = "/home/karim/weather-stations-monitoring-system/parquet-transformer/src/parqFiles/" + entry.getKey();
        java.io.File dir = new java.io.File(partitionPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Path path = new Path(partitionPath + "/data.parquet");
        Schema schema = ReflectData.AllowNull.get().getSchema(WeatherStationMsg.class);

        try (ParquetWriter<WeatherStationMsg> writer = AvroParquetWriter
                .<WeatherStationMsg>builder(path)
                .withSchema(schema)
                .withDataModel(ReflectData.get())
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .withConf(new Configuration())
                .build()) {
            for (WeatherStationMsg record : entry.getValue()) {
                writer.write(record);
            }
        }
    }
}

    
}