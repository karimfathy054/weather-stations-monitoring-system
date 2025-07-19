package org.example;

public class ValueEntry {
    private int offset;
    private int fileId;

    public ValueEntry(int offset, int fileId) {
        this.offset = offset;
        this.fileId = fileId;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getFileId() {
        return fileId;
    }

    public void setFileId(int fileId) {
        this.fileId = fileId;
    }
}