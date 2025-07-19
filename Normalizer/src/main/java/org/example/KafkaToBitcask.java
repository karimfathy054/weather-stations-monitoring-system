package org.example;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class KafkaToBitcask {

    public static void main(String[] args) {

        String KAFKA_BOOTSTRAP_SERVERS = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (KAFKA_BOOTSTRAP_SERVERS == null) {
            System.err.println("Environment variable KAFKA_BOOTSTRAP_SERVERS is not set.");
            System.exit(1);
        }
        String KAFKA_TOPIC = System.getenv("KAFKA_TOPIC");
        if (KAFKA_TOPIC == null) {
            System.err.println("Environment variable KAFKA_TOPIC is not set.");
            System.exit(1);
        }
        
        String BITCASK_SERVER_HOST = System.getenv("BITCASK_SERVER_HOST");
        if (BITCASK_SERVER_HOST == null) {
            System.err.println("Environment variable BITCASK_SERVER_HOST is not set.");
            System.exit(1);
        }
        
        String BITCASK_SERVER_PORT_str = System.getenv("BITCASK_SERVER_PORT");
        if (BITCASK_SERVER_PORT_str == null) {
            System.err.println("Environment variable BITCASK_SERVER_PORT is not set.");
            System.exit(1);
        }
        int BITCASK_SERVER_PORT = Integer.parseInt(BITCASK_SERVER_PORT_str);
        // Check if the environment variables are set
        if (KAFKA_BOOTSTRAP_SERVERS.isEmpty() || BITCASK_SERVER_HOST.isEmpty() || BITCASK_SERVER_PORT_str.isEmpty()) {
            System.err.println("Environment variables are not set.");
            System.exit(1);
        }
        // Kafka Streams Configuration
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "message-normailzer");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        // Define Stream Processing
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> stream = builder.stream(KAFKA_TOPIC);

        stream.foreach((key, value) -> {
            try {
                Long id = Long.parseLong(key);

                // Send to Bitcask server via TCP
                sendToBitcask(id, value, BITCASK_SERVER_HOST, BITCASK_SERVER_PORT);  // change host/port as needed
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    public static void sendToBitcask(Long key, String value, String host, int port) throws IOException {
        try (Socket socket = new Socket(host, port);
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)){

            value = value.replace(',',';');
            String request = "w" + " " + key + " " + value + "--no-reply";
            writer.println(request);

        }  catch (IOException e) {
            e.printStackTrace();
        }
    }
}
