package org.example;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogPathTracker implements Runnable {
    private final String logPath;
    private final long pollingIntervalMs;
    private final ExecutorService executor;
    private final int maxNumOfFiles;

    public LogPathTracker(String logPath, long pollingIntervalMs, int maxNumOfFiles) {
        this.logPath = logPath;
        this.pollingIntervalMs = pollingIntervalMs;
        this.maxNumOfFiles = maxNumOfFiles;
        this.executor = Executors.newSingleThreadExecutor();
    }

    private int countFiles() {
        File dir = new File(logPath);
        if (!dir.exists() || !dir.isDirectory()) return 0;
        return dir.listFiles() != null ? dir.listFiles().length : 0;
    }

    public void start() {
        executor.submit(this);
    }

    public void stop() {
        executor.shutdownNow();
    }

    @Override
    public void run() {
        ExecutorService com = Executors.newFixedThreadPool(1);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int currentCount = countFiles();

                if (currentCount >= maxNumOfFiles) {
                    // Notify the worker asynchronously
                    executor.submit((Runnable) com.submit(new Worker("c")));
                }

                Thread.sleep(pollingIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
