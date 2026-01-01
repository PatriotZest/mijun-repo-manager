package com.mijun;

public class GitBlob extends GitObject {
    public static final byte[] fmt = "blob".getBytes(); 
    byte[] blobdata;

    public GitBlob(byte[] data) {
        this.blobdata = data;
    }

    @Override
    public byte[] serialize() {
        return this.data;
    }

    @Override
    public void deserialize(byte[] data) {
        this.data = data;
    }

    @Override
    public byte[] type() {
        return fmt;
    }
    
}
