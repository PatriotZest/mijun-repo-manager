package com.mijun;

import java.util.zip.*;
import java.util.zip.DataFormatException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class GitObject {
    byte[] data;
    String string;

    public GitObject(byte[] data) {
        if (data != null) {
            this.deserialize(data);
        } else {
            this.data = data;
        }
    }

    public GitObject() {
        this.data = null;
    }

    
    public abstract byte[] serialize();

    public abstract void deserialize(byte[] data);

    public Object object_read(String repo, byte[] sha) {
    /*  Path path = repo_file(repo, "objects", sha.toString().substring(0, 2), sha..toString().substring(2, sha.length));
        byte[] raw = GitObject.decompress(Files.readAllBytes(path));
        int indexType = 0;
        for (int i = 0; i < raw.length; i++) {
            if(raw[i] == (byte) ' ') {
                indexType = i;
                break;
            }
        }
        byte[] fmt = new byte[indexType - 1];
        for (int i = 0; i < indexType; i++) {
            fmt[i] = raw[i];
        }
        
        int compareIndex = 0;
        for (int i = 0; i < indexType; i++) {
            if (raw[i] == '\x00') {
                compareIndex = i;
                break;
            }
        }
        
        String rawString = GitObject.decode(raw);
        int size = Integer.parseInt(rawString.substring(indexType, compareIndex));
        if (size != raw.length - compareIndex - 1) {
            throw new Exception("Malformed object: bad length")
        }

        // fk python
        Class<? extends GitObject> c;
        switch(fmt){
            case 'blob': c = new GitBlob(data); break;
            default: throw new IllegalArgumentException("No type matched");
        }

        return c;
    */
        
        return null;
    }

    public static byte[] object_write(GitObject obj) {
        try {
            byte[] data = obj.serialize();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(obj.getClass().toString().getBytes());
            out.write(' ');
            out.write(String.valueOf(data.length).getBytes());
            out.write(0);
            out.write(data);
            byte[] result = out.toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha = md.digest(result);
            return sha;
        } catch (IOException e) {
            System.out.println("no bueno with IOException");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No algorithm exists like this, but SHA-1 always exists?");
        }
        return null;
    }

    public static byte[] object_write(GitObject obj, String repo) {
        try {
            byte[] data = obj.serialize();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(obj.getClass().toString().getBytes());
            out.write(' ');
            out.write(String.valueOf(data.length).getBytes());
            out.write(0);
            out.write(data);
            byte[] result = out.toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha = md.digest(result);
        /*  Path path = repo_file(repo, "objects", sha.toString().substring(0, 2), sha.toString().substring(2, sha.length), mkdir = True );
            if (path doesnt exist) {
                Files.write(GitObject.compress(result));
            }        
        */
            return sha;
        } catch (IOException e) {
            System.out.println("no bueno with IOException");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No algorithm exists like this, but SHA-1 always exists?");
        }
        return null;
    }
    public static byte[] compress(byte[] byteInput) {
        // fixed size of 20 bytes, possible pitfall being that its not a ze dynamic byte array? idk how about that gng
        byte[] output = new byte[20];

        // java.util.zip shi        
        Deflater compressor = new Deflater();
        compressor.setInput(byteInput);
        compressor.finish();

        int compressedDataLength = compressor.deflate(output);
        compressor.end();
        return output;
    }

    public static byte[] compress(String input) {
        // get ze string and convert into ze bytes
        byte[] byteInput = input.getBytes();
        
        // fixed size of 100 bytes, possible pitfall being that its not a ze dynamic byte array? idk how about that gng
        byte[] output = new byte[100];

        // java.util.zip shi        
        Deflater compressor = new Deflater();
        compressor.setInput(byteInput);
        compressor.finish();

        int compressedDataLength = compressor.deflate(output);
        compressor.end();
        return output;
    }

    public static byte[] decompress(byte[] output) {
        try {
            Inflater decompressor = new Inflater();
            decompressor.setInput(output);

            // same pitfall of dynamic byte size
            byte[] result = new byte[100];
            int resultLength = decompressor.inflate(result);
            decompressor.end();
            return result;
        } catch (DataFormatException e) {
            System.out.println("Wrong format, no bueno");
        }
        return null;
    }

    public static String decode(byte[] result) {
        return new String(result);
    }
}
