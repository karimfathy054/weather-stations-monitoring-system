package org.example;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Worker implements Callable<byte[]> {
    static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    static HashMap<Long,ValueEntry> inMemory;
    static HashMap<Long,Integer> hintMap;
    static ByteBuffer buffer;
    static int fileId;
    static String filePath;
    static String hintPath;
    static int maxBufferSize;
    static int keySize;
    static int valueSize;
    static int offsetSize;
    static int startCompactionIdx;
    private String request;

    public Worker(int fileId, String filePath, String hintPath) throws IOException {
        maxBufferSize = 8192;
        hintMap = new HashMap<>();
        buffer = ByteBuffer.allocate(maxBufferSize);
        keySize = 8;
        valueSize = 4;
        offsetSize = 4;
        Worker.fileId = fileId;
        Worker.filePath = filePath;
        Worker.hintPath = hintPath;
        inMemory = getInMemory();
    }

    public Worker(String request){
        this.request = request;
    }

    private File[] getFilesFromFolder(String path){
        File[] files = null;
        File folder = new File(path);
        if(folder.isDirectory()){
            files = folder.listFiles();
        }

        // Ensure it's a directory
        if (folder.isDirectory()) {
            files = folder.listFiles();

            if (files != null) {
                Arrays.sort(files, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));
            } else {
                System.out.println("No files found or an error occurred.");
            }
        } else {
            System.out.println("The path provided is not a directory.");
        }

        return files;
    }

    private HashMap<Long,ValueEntry> getInMemory() throws IOException {
        File[] hintFiles = getFilesFromFolder(hintPath);
        if(hintFiles == null || hintFiles.length == 0 )
            return new HashMap<>();

        HashMap<Long, ValueEntry> map = new HashMap<>();
        int fileId = 0;
        for(File file: hintFiles){
            ByteBuffer byteBuffer = readFileToByteBuffer(file);
            fileId = Integer.parseInt(file.getName());
            while(byteBuffer.hasRemaining()){
                long key = byteBuffer.getLong();
                int offset = byteBuffer.getInt();
                map.put(key, new ValueEntry(offset, fileId));
            }
        }
        Worker.fileId = fileId + 1;
        System.out.println("Bitcask InMemory has been read correctly.");

        return map;
    }

    private void writeToDisk(ByteBuffer byteBuffer, String fileId, String path){
        // Flip the buffer before writing (prepare for reading)
        byteBuffer.flip();

        try (FileOutputStream fos = new FileOutputStream(path + fileId);
             FileChannel channel = fos.getChannel()) {

            channel.write(byteBuffer);
            System.out.println("Data written to disk successfully.");

            // Clear the buffer.
            byteBuffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeHintMapToDisk(HashMap<Long, Integer> hintMap, String fileId, String path){
        // Hint Map ---> Key, Offset

        // key , offset
        // and get the file id from the hint file name when recreating the hashmap.
        ByteBuffer hintBuffer = ByteBuffer.allocate(maxBufferSize);
        for(Map.Entry<Long, Integer> entry : hintMap.entrySet()){
            long key = entry.getKey();
            int offset = entry.getValue();

            hintBuffer.putLong(key);

            hintBuffer.putInt(offset);
        }

        System.out.println("Saving Hint Map in File " + fileId);
        writeToDisk(hintBuffer, fileId, path);

        hintMap.clear();
    }

    private void writeRequest(long key, String value){
        lock.writeLock().lock();  // Blocks all readers and other writers
        try {
            // Value Size, Key, Value
            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

            int entrySize = valueBytes.length + keySize + valueSize;

            if(entrySize + buffer.position() > maxBufferSize){
                writeToDisk(buffer, String.valueOf(fileId), filePath);
                writeHintMapToDisk(hintMap, String.valueOf(fileId) , hintPath);
                fileId += 1;
            }
            inMemory.put(key, new ValueEntry(buffer.position(), fileId));
            hintMap.put(key, buffer.position());

            // Add Value Size to the buffer with size long -> valueSize = 8
            buffer.putInt(valueBytes.length);

            // Add the key to the buffer with size long -> keySize = 8
            buffer.putLong(key);

            // Add the value to the buffer with size valueBytes.length
            buffer.put(valueBytes);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private byte[] readRequest(long key){
        lock.readLock().lock();  // Multiple threads can read at the same time
        byte[] value;
        try {
            // Check if the key is in the memory
            if(!inMemory.containsKey(key)){
                //TODO
//                throw new RuntimeException("Can't Find Key: " + key);
                System.out.println("Can't Find Key: " + key);
                return null;
            }

            // Get the offset and fileID from the memory
            ValueEntry valueEntry = inMemory.get(key);
            int offset = valueEntry.getOffset();
            int entryFileId = valueEntry.getFileId();

            ByteBuffer duplicate = null;
            if(entryFileId != fileId){
                try (FileInputStream fis = new FileInputStream(filePath + entryFileId);
                     FileChannel fileChannel = fis.getChannel()) {

                    ByteBuffer tempBuffer = ByteBuffer.allocate(maxBufferSize);
                    fileChannel.read(tempBuffer); // Reads data into buffer
                    tempBuffer.flip(); // Prepare buffer for reading
                    duplicate = tempBuffer.duplicate();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else {
                duplicate = buffer.duplicate();
            }

            duplicate.position(offset);
            int valueSize = duplicate.getInt();
            long entryKey = duplicate.getLong();
            if(key != entryKey){
                throw new RuntimeException("The key you provided not equal the key that has been found in the file.");
            }

            value = new byte[valueSize];
            duplicate.get(value);
        } finally {
            lock.readLock().unlock();
        }
        return value;
    }

    private ByteBuffer readFileToByteBuffer(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel channel = fis.getChannel()) {

            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.flip(); // Prepare for reading
            return buffer;
        }
    }

    private void fillCompactionMap(HashMap<Long, byte[]> compactionMap, ByteBuffer tmpBuffer){
        while(tmpBuffer.hasRemaining()){
            int valueSize = tmpBuffer.getInt();
            long key = tmpBuffer.getLong();
            byte[] value = new byte[valueSize];
            tmpBuffer.get(value);

            compactionMap.put(key, value);
        }
    }

    // if inMemory has space
    // 10 11
    // r,r,r ... ,r   c_part1, w --> fileId w w w w  , c_part2
    public void compaction(){
        // Compaction Algorithm
        lock.readLock().lock();
        System.out.println("Starting Compaction....");
        startCompactionIdx = fileId - 1;

        HashMap<Long,byte[]> compactionMap = new HashMap<>();
        File[] files = null;
        File[] hintFiles = null;
        try {
            files = getFilesFromFolder(filePath);
            hintFiles = getFilesFromFolder(hintPath);
            for (File file : files) {
                ByteBuffer tmpBuffer = readFileToByteBuffer(file);
                fillCompactionMap(compactionMap, tmpBuffer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            lock.readLock().unlock();
        }

        // Saving Compaction Map to New File
        // InMemory Modification
        lock.writeLock().lock();
        try{
            if(hintFiles != null){
                for (File file : hintFiles) {
                    boolean deleted = file.delete(); // Delete the file after processing

                    if (!deleted) {
                        System.err.println("Failed to delete file: " + file.getName());
                    }
                }
            }
            if (files != null) {
                for (File file : files) {
                    boolean deleted = file.delete(); // Delete the file after processing

                    if (!deleted) {
                        System.err.println("Failed to delete file: " + file.getName());
                    }
                }
            }
            ByteBuffer compactionBuffer = ByteBuffer.allocate(maxBufferSize);

            ByteBuffer compactionHintBuffer = ByteBuffer.allocate(maxBufferSize);
            for(Map.Entry<Long, byte[]> entry : compactionMap.entrySet()){
                long key = entry.getKey();
                byte[] value = entry.getValue();

                int entrySize = value.length + keySize + valueSize;

                if(entrySize + compactionBuffer.position() > maxBufferSize){
                    System.out.println("Saving Compaction Map in File " + startCompactionIdx);
                    writeToDisk(compactionBuffer, String.valueOf(startCompactionIdx), filePath);
                    writeToDisk(compactionHintBuffer, String.valueOf(startCompactionIdx) , hintPath);
                    startCompactionIdx -= 1;
                }

                if(inMemory.containsKey(key)){
                    if(inMemory.get(key).getFileId() < fileId){
                        inMemory.put(key, new ValueEntry(compactionBuffer.position(), startCompactionIdx));
                    }
                }
                else {
                    throw new RuntimeException("Key not in Memory!!!");
                }
                compactionHintBuffer.putLong(key);
                compactionHintBuffer.putInt(compactionBuffer.position());

                // Add Value Size to the buffer with size long -> valueSize = 8
                compactionBuffer.putInt(value.length);

                // Add the key to the buffer with size long -> keySize = 8
                compactionBuffer.putLong(key);

                // Add the value to the buffer with size valueBytes.length
                compactionBuffer.put(value);
            }
            System.out.println("Saving Compaction Map in File " + startCompactionIdx);
            writeToDisk(compactionBuffer, String.valueOf(startCompactionIdx), filePath);
            writeToDisk(compactionHintBuffer, String.valueOf(startCompactionIdx), hintPath);
        }finally {
            lock.writeLock().unlock();
        }
    }

    private byte[] saveAllKeysAndValues(String path, String fileName){
        lock.readLock().lock();
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(byteOut, StandardCharsets.UTF_8))) {
                writer.write("Key,Value");
                writer.newLine();

                for (Long key : inMemory.keySet()) {
                    byte[] valueBytes = readRequest(key);
                    String value = valueBytes != null ? new String(valueBytes, StandardCharsets.UTF_8) : "";
                    writer.write(key + "," + value);
                    writer.newLine();
                }

                writer.flush(); // Important: flush the writer to ensure all data is written to the stream
            }


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.readLock().unlock();
        }

        return byteOut.toByteArray();
    }

    @Override
    public byte[] call(){
        char req = request.charAt(0);
        String[] requestParts;
        long key;
        switch (req){
            // WRITE REQUEST
            case 'w':
                requestParts = request.split(" ", 3);
                key = Long.parseLong(requestParts[1]);
                String value = requestParts[2];
                writeRequest(key, value);
                break;

            // READ REQUEST
            case 'r':
                requestParts = request.split(" ", 2);
                key = Long.parseLong(requestParts[1]);
                return readRequest(key);

            // COMPACTION REQUEST
            case 'c':
                compaction();
                break;

            // SAVE ALL KEYS AND VALUES TO A FILE NAME WITH THE TIMESTAMP OF THE CURRENT TIME WITH EXTENSION .csv
            case 'a':
                requestParts = request.split(" ",3);
                return saveAllKeysAndValues(requestParts[1], requestParts[2]);
        }
        return null;
    }
}
