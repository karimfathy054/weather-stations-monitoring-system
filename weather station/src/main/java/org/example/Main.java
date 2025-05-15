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

        long stationId = args[0] != null ? Long.parseLong(args[0]) : 1;
        //fixme: load last message counter from db
        long statusMsgCounter = 0;

        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        ObjectMapper objectMapper = new ObjectMapper();
        try(Producer<String,String> producer = new KafkaProducer<>(props)){

            while(true){
                if (dropMsg()){
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