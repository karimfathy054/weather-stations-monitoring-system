package org.example;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BitcaskClient {
    private static final String SERVER_HOST = "localhost"; // change if needed
    private static final int SERVER_PORT = 5000; // change if needed
    private static final String PATH = "/home/karim/Weather-Stations-Monitoring/BitcaskClient/results/";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("--view-all");
            System.out.println("--view --key=SOME_KEY");
            System.out.println("--perf --clients=N");
            return;
        }

        if (args[0].equals("--view-all")) {
            viewAll();

        } else if (args[0].equals("--view") && args.length == 2 && args[1].startsWith("--key=")) {
            String key = args[1].substring("--key=".length());
            viewKey(key);

        } else if (args[0].equals("--perf") && args.length == 2 && args[1].startsWith("--clients=")) {
            int clients = Integer.parseInt(args[1].substring("--clients=".length()));
            perfTest(clients);

        } else {
            System.err.println("Invalid arguments.");
        }
    }

    private static void viewAll() {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             InputStream inputStream = socket.getInputStream()) {

            long timestamp = Instant.now().getEpochSecond();
            String fileName = timestamp + ".csv";
            Path outputPath = Paths.get(PATH + fileName);

            System.out.println("Sending...");
            writer.write("a " + PATH + " " + fileName + "\n");
            writer.flush();
            socket.shutdownOutput(); // Optional, tells server no more data is being sent

            // Receive byte[] data and write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath.toFile())) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("Succeeded '_'");
            System.out.println("Written to file: " + fileName);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write the file.");
        }
    }

    private static void viewAllWithThread(long timestamp, int threadId) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             InputStream inputStream = socket.getInputStream()) {

            String fileName = timestamp + "_thread_" + threadId + ".csv";
            Path outputPath = Paths.get(PATH + fileName);

            System.out.println("Sending...");
            writer.write("a " + PATH + " " + fileName + "\n");
            writer.flush();
            socket.shutdownOutput(); // Optional, tells server no more data is being sent

            // Receive byte[] data and write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath.toFile())) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("Succeeded '_'");
            System.out.println("Written to file: " + fileName);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write the file.");
        }
    }


    private static void viewKey(String key) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            writer.write("r " + key + "\n");
            writer.flush();
            socket.shutdownOutput();

            String response = reader.readLine();
            if (response != null) {
                System.out.println("Value: " + response);
            } else {
                System.out.println("Key not found.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void perfTest(int numClients) {
        ExecutorService executor = Executors.newFixedThreadPool(numClients);
        long timestamp = Instant.now().getEpochSecond();

        for (int i = 0; i < numClients; i++) {
            final int threadId = i + 1;
            executor.submit(() -> {
                viewAllWithThread(timestamp, threadId);
            });
        }

        executor.shutdown();
    }

}
