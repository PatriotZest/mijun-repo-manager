package com.mijun;

import java.util.zip.*;

import com.mijun.libmijun.GitRepository;

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

    public abstract byte[] type();

    public abstract byte[] serialize();

    public abstract void deserialize(byte[] data);

    public static GitObject object_read(GitRepository repo, byte[] sha) {
        try {
            String shaString = libmijun.toHex(sha);
            Path path = libmijun.repoFile(repo, false, "objects", shaString.substring(0, 2), shaString.substring(2));
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
                if (raw[i] == '0') {
                    compareIndex = i;
                    break;
                }
            }
            
            String rawString = GitObject.decode(raw);
            int size = Integer.parseInt(rawString.substring(indexType, compareIndex));
            if (size != raw.length - compareIndex - 1) {
                throw new IllegalArgumentException("Malformed length");
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = compareIndex + 1; i < raw.length; i++) {
                out.write(raw[i]);
            }
            byte[] rawSlice = out.toByteArray();
            // fk python
            GitObject c;
            switch(new String(fmt)){
                case "blob": c = new GitBlob(rawSlice); break;
                // {Misha you need to add other data types here}
                default: throw new IllegalArgumentException("No type matched");
            }
            return c;
        } catch (IOException e) {
            System.out.println("No bueno");
        }
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

    public static byte[] object_write(GitObject obj, GitRepository repo) {
        try {
            byte[] data = obj.serialize();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(obj.type());
            out.write(' ');
            out.write(String.valueOf(data.length).getBytes());
            out.write(0);
            out.write(data);
            byte[] result = out.toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha = md.digest(result);
            String shaString = libmijun.toHex(sha);
            Path path = libmijun.repoFile(
                repo, 
                true,
                "objects",
                shaString.substring(0, 2),
                shaString.substring(2)
            );    
            if (Files.notExists(path)) {
                Files.createFile(path);
                Files.write(path, GitObject.compress(result));
            }
            return sha;
        } catch (IOException e) {
            System.out.println("no bueno with IOException");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No algorithm exists like this, but SHA-1 always exists?");
        }
        
        return null;
    }
    
    public static byte[] object_find(GitRepository repo, byte[] name, byte[] fmt, boolean follow) {
        return name;
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
