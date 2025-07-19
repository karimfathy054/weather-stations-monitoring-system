package org.example;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.Random;

public class Main {
    private static final Random rand = new Random();

    public static void main(String[] args) throws InterruptedException {

        long stationId = args.length > 0 ? Long.parseLong(args[0]) : 1;
        //fixme: load last message counter from db
        long statusMsgCounter = 0;

        // Read Kafka configuration from environment variables with defaults
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            bootstrapServers = "kafka:9092"; // Default to Kubernetes service name
        }
        
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Add reliability configurations
        props.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        props.setProperty(ProducerConfig.RETRIES_CONFIG, "10");
        props.setProperty(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");
        props.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");
        
        System.out.println("Connecting to Kafka at: " + bootstrapServers);

        ObjectMapper objectMapper = new ObjectMapper();
        try(Producer<String,String> producer = new KafkaProducer<>(props)){
            System.out.println("Weather station " + stationId + " started. Sending data to Kafka...");

            while(true){
                if (dropMsg()){
                    statusMsgCounter++;
                    System.out.println("Message dropped");
                    continue;
                }
                WeatherStationMsg msg = createWeatherStationMsg(stationId, statusMsgCounter++);
                try {
                    String jsonMsg = objectMapper.writeValueAsString(msg);
                    ProducerRecord<String,String> record = new ProducerRecord<>("weather-station", String.valueOf(stationId), jsonMsg);
                    producer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            System.out.println("Error sending message: " + exception.getMessage());
                        } else {
                            System.out.println("Message sent to topic " + metadata.topic() + " partition " + metadata.partition() + " offset " + metadata.offset());
                        }
                    });
                } catch (JsonProcessingException e) {
                    System.out.println("message to json error");
                    throw new RuntimeException(e);
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.err.println("Fatal error connecting to Kafka: " + e.getMessage());
            e.printStackTrace();
            // Wait before attempting restart (in case this is in a Kubernetes restart loop)
            Thread.sleep(5000);
            throw e;
        }
    }
    private static boolean dropMsg(){
        return rand.nextInt(100) < 10; // 10% chance to drop the message
    }
    private static WeatherStationMsg createWeatherStationMsg(long stationId, long statusMsgCounter) {

        int temperature = rand.nextInt(40,100) ;
        int humidity = rand.nextInt(101);
        int windSpeed = rand.nextInt(26);

        WeatherData weatherData = new WeatherData(temperature, humidity, windSpeed);
        int batteryLevel = rand.nextInt(101);

        BatteryStatus batteryStatus ;
        if (batteryLevel>=70){
            batteryStatus = BatteryStatus.HIGH;
        } else if (batteryLevel>=30){
            batteryStatus = BatteryStatus.MEDIUM;
        } else {
            batteryStatus = BatteryStatus.LOW;
        }
        return new WeatherStationMsg(stationId, statusMsgCounter, batteryStatus, weatherData);
    }
}