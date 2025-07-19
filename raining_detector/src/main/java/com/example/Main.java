package com.example;



import java.util.Properties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {
    public static void main(String[] args) {

        Properties props = new Properties();
        props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG,"rain-detector");
        props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
        props.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,Serdes.String().getClass().getName());
        props.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,Serdes.String().getClass().getName());


        ObjectMapper objMapper = new ObjectMapper();
        
        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> inputStream = builder.stream("weather-station");

        KStream<String,String> rainStream = inputStream.filter((key, value) -> {
            try {
                JsonNode jsonNode = objMapper.readTree(value);
                int humidity = jsonNode.get("weather").get("humidity").asInt();
                return humidity >= 70; 
            } catch (Exception e) {   
                e.printStackTrace();
                return false;
            }
        }).mapValues((value) ->{
            try{
                
                JsonNode jsNode = objMapper.readTree(value);
                int stationId = jsNode.get("station_id").asInt();
                int humidity = jsNode.get("weather").get("humidity").asInt();
                String s = "Rain detected at station ID: " + stationId + " at humidity level: " + humidity;
                System.out.println(s); 
                return s;
            }
            catch(Exception e){
                e.printStackTrace();
                return "Error processing message";
            }
        });

        rainStream.to("rain-alerts");

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));



       

    }
}