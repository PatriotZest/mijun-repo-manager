package com.mijun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.*;

import com.mijun.libmijun.GitRepository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
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
            byte[] fmt = new byte[indexType];
            for (int i = 0; i < indexType; i++) {
                fmt[i] = raw[i];
            }
            
            int compareIndex = 0; 
            for (int i = indexType + 1; i < raw.length; i++) {
                if (raw[i] == 0) {
                    compareIndex = i;
                    break;
                }
            }
            
            String rawString = GitObject.decode(raw);
            int size = Integer.parseInt(rawString.substring(indexType + 1, compareIndex));
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
            // {Misha you need to add commit here :3} - DONE
            switch(new String(fmt)){
                case "blob": c = new GitBlob(rawSlice); break;
                case "commit": c = new GitCommit(rawSlice); break;
                case "tree": c = new GitTree(rawSlice); break;
                case "tag": c = new GitTag(rawSlice); break;
                
                default: throw new IllegalArgumentException("No type matched");
            }
            return c;
        } catch (IOException e) {
            System.out.println("No bueno");
        }
        return null;
    }
    
    // running into error here , making a change {Arjun}
    public static byte[] object_write(GitObject obj) {
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
    
    // {Arjun, completed this method}
    // 7.6 object_find
    // finds an object by name and type, optionally following tags and commits
    public static byte[] object_find(GitRepository repo, String name, byte[] fmt, boolean follow) {
        List<byte[]> candidates = object_resolve(repo, name);
        
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No such reference");
        }

        byte[] sha = candidates.get(0);
        
        if (fmt == null) {
            return sha;
        }
        
        while (true) {
            GitObject obj = object_read(repo, sha);
            
            if (obj == null) return null;

            if (Arrays.equals(obj.type(), fmt)) {
                return sha;
            }

            if (!follow) {
                return null;
            }

            if (Arrays.equals(obj.type(), "tag".getBytes())) {
                GitTag tag = (GitTag) obj;
                sha = ((byte[]) tag.kvlm.get("object".getBytes()));
                continue;
            }

            if (Arrays.equals(obj.type(), "commit".getBytes()) && Arrays.equals(fmt, "tree".getBytes())) {
                GitCommit commit = (GitCommit) obj;
                sha = ((byte[]) commit.kvlm.get("tree".getBytes()));
                continue;
            }
            return null;
        }
    }


    static List<byte[]> object_resolve(
        GitRepository repo,
        String name) {

        List<byte[]> out = new ArrayList<>();

        if (name.equals("HEAD")) {
            try {
                byte[] sha = GitRef.refResolve(repo, "HEAD");
                if (sha != null) out.add(sha);
            } catch (IOException ignored) {}
            return out;
        }   

        if (looksLikeHex(name)) {
            name = name.toLowerCase();
            Path objDir = libmijun.repoPath(
                    repo, "objects", name.substring(0, 2));

            if (Files.exists(objDir)) {
                try (DirectoryStream<Path> ds =
                        Files.newDirectoryStream(objDir)) {
                    for (Path p : ds) {
                        if (p.getFileName().toString()
                                .startsWith(name.substring(2))) {
                            out.add(libmijun.fromHexString(
                                    name.substring(0, 2) +
                                    p.getFileName().toString()));
                        }
                    }
                } catch (IOException ignored) {}
            }
        }

        try {
            byte[] tag = GitRef.refResolve(repo,
                    "refs/tags/" + name);
            if (tag != null) out.add(tag);

            byte[] branch = GitRef.refResolve(repo,
                "refs/heads/" + name);

            if (branch != null) out.add(branch);
        } catch (IOException ignored) {}
        
        if (out.isEmpty() && !looksLikeHex(name)) {
            return out;
        }
        return out;
    }


    public static byte[] compress(byte[] byteInput) {
        // dynamic byte size now
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        
        Deflater compressor = new Deflater();
        compressor.setInput(byteInput);
        compressor.finish();
        
        while(!compressor.finished()) {
            int count = compressor.deflate(buffer);
            out.write(buffer, 0, count);
        }

        compressor.end();
        return out.toByteArray();
    }

    public static byte[] decompress(byte[] output) {
        try {
            Inflater decompressor = new Inflater();
            decompressor.setInput(output);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!decompressor.finished()) {
                int count = decompressor.inflate(buffer);
                out.write(buffer, 0, count);
            }
            decompressor.end();
            return out.toByteArray();
        } catch (DataFormatException e) {
            System.out.println("Wrong format, no bueno");
        }
        return null;
    }

    public static String decode(byte[] result) {
        return new String(result);
    }
    
    // helper method to check if a string looks like a hex SHA-1
    static boolean looksLikeHex(String s) {
        if (s.length() < 4) return false;
        for (char c : s.toCharArray()) {
            if (!((c >= '0' && c <= '9') ||
                (c >= 'a' && c <= 'f') ||
                (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}