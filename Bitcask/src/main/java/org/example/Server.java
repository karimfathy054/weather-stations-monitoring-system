package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class Server {
    private static void handleClient(Socket socket, int numThreads) {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<byte[]>> futures = new ArrayList<>();
        List<byte[]> results = new ArrayList<>();

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));)
        {
            String message;

            while ((message = reader.readLine()) != null) {
                if (message.isEmpty()) continue;

                if (message.startsWith("w ") || message.startsWith("r ") || message.startsWith("a ")) {

                    boolean expectsReply = !message.contains("--no-reply");
                    String cleanedMessage = message.replace("--no-reply", "").trim();

                    Future<byte[]> future = executor.submit(new Worker(cleanedMessage));

                    if(expectsReply){
                        futures.add(future);
                    }
                } else {
                    System.err.println("Unrecognized message format: " + message);
                }
            }

            for (Future<byte[]> future : futures) {
                try {
                    byte[] result = future.get();
                    if (result != null) {
                        writer.write(new String(result) + "\n");
                        writer.flush();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
            try { socket.close(); } catch (Exception ignored) {}
        }
    }


    public static void main(String args[]) throws IOException {
        Properties config = new Properties();
        FileInputStream fis = new FileInputStream("/home/karim/Weather-Stations-Monitoring/Bitcask/src/main/resources/system.properties");
        config.load(fis);

        int serverPort = Integer.parseInt(config.getProperty("server.port"));
        String logsPath = config.getProperty("server.logs");
        String hintPath = config.getProperty("server.hints");
        int numThreads = Integer.parseInt(config.getProperty("server.threads"));

        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("Bitcask Server started. Listening on tcp port " + serverPort);

            Worker worker = new Worker(0, logsPath, hintPath);

            // Poll every 5 seconds (5000 ms)
            LogPathTracker tracker = new LogPathTracker(logsPath, 5000, 10);
            tracker.start();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket, numThreads)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
