package com.mijun;

import com.mijun.libmijun.GitRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

public abstract class GitObject {

    protected byte[] data;

    public GitObject(byte[] data) {
        if (data != null) {
            deserialize(data);
        } else {
            this.data = null;
        }
    }

    public GitObject() {
        this.data = null;
    }

    public abstract byte[] type();

    public abstract byte[] serialize();

    public abstract void deserialize(byte[] data);

    // -------- Object read --------
    public static GitObject object_read(GitRepository repo, byte[] sha) {
        try {
            String shaStr = libmijun.toHex(sha);
            Path objPath = libmijun.repoFile(repo, false,
                    "objects", shaStr.substring(0, 2), shaStr.substring(2));

            if (!Files.exists(objPath)) return null;

            byte[] compressed = Files.readAllBytes(objPath);
            byte[] raw = decompress(compressed);

            int spaceIdx = 0;
            while (raw[spaceIdx] != ' ') spaceIdx++;

            int nullIdx = spaceIdx + 1;
            while (raw[nullIdx] != 0) nullIdx++;

            String typeStr = new String(raw, 0, spaceIdx);
            int size = Integer.parseInt(new String(raw, spaceIdx + 1, nullIdx - spaceIdx - 1));

            byte[] content = Arrays.copyOfRange(raw, nullIdx + 1, raw.length);

            if (size != content.length) {
                throw new IllegalArgumentException("Malformed object: size mismatch");
            }

            switch (typeStr) {
                case "blob": return new GitBlob(content);
                case "commit": return new GitCommit(content);
                case "tree": {
                    GitTree tree = new GitTree();
                    tree.deserialize(content);
                    return tree;
                }
                case "tag": return new GitTag(content);
                default: throw new IllegalArgumentException("Unknown type: " + typeStr);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read object", e);
        }
    }

    // -------- Object write (compute SHA) --------
    public static byte[] object_write(GitObject obj) {
        try {
            byte[] content = obj.serialize();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(obj.type());
            out.write(' ');
            out.write(String.valueOf(content.length).getBytes());
            out.write(0);
            out.write(content);
            byte[] full = out.toByteArray();

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(full);

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // -------- Object write + save to repo --------
    public static byte[] object_write(GitObject obj, GitRepository repo) {
        try {
            byte[] content = obj.serialize();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(obj.type());
            out.write(' ');
            out.write(String.valueOf(content.length).getBytes());
            out.write(0);
            out.write(content);

            byte[] full = out.toByteArray();
            byte[] sha = MessageDigest.getInstance("SHA-1").digest(full);
            String shaStr = libmijun.toHex(sha);

            Path objPath = libmijun.repoFile(repo, true,
                    "objects", shaStr.substring(0, 2), shaStr.substring(2));

            if (!Files.exists(objPath)) {
                Files.createFile(objPath);
                Files.write(objPath, compress(full));
            }

            return sha;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // -------- Object find --------
    public static byte[] object_find(GitRepository repo, String name, byte[] fmt, boolean follow) {
        List<byte[]> candidates = object_resolve(repo, name);
        if (candidates.isEmpty()) throw new IllegalArgumentException("No such reference");

        byte[] sha = candidates.get(0);
        if (fmt == null) return sha;

        while (true) {
            GitObject obj = object_read(repo, sha);
            if (obj == null) return null;

            if (Arrays.equals(obj.type(), fmt)) return sha;
            if (!follow) return null;

            if (Arrays.equals(obj.type(), "tag".getBytes())) {
                GitTag tag = (GitTag) obj;
                sha = (byte[]) tag.kvlm.get("object".getBytes());
                continue;
            }

            if (Arrays.equals(obj.type(), "commit".getBytes()) &&
                Arrays.equals(fmt, "tree".getBytes())) {
                GitCommit commit = (GitCommit) obj;
                sha = (byte[]) commit.kvlm.get("tree".getBytes());
                continue;
            }

            return null;
        }
    }

    // -------- Resolve candidate refs --------
    private static List<byte[]> object_resolve(GitRepository repo, String name) {
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
            Path objDir = libmijun.repoPath(repo, "objects", name.substring(0, 2));
            if (Files.exists(objDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(objDir)) {
                    for (Path p : ds) {
                        if (p.getFileName().toString().startsWith(name.substring(2))) {
                            String hexStr = name.substring(0, 2) + p.getFileName().toString();
                            out.add(hexStringToByteArray(hexStr));
                        }
                    }
                } catch (IOException ignored) {}
            }
        }

        try {
            byte[] tag = GitRef.refResolve(repo, "refs/tags/" + name);
            if (tag != null) out.add(tag);
            byte[] branch = GitRef.refResolve(repo, "refs/heads/" + name);
            if (branch != null) out.add(branch);
        } catch (IOException ignored) {}

        return out;
    }

    // -------- Compression / Decompression --------
    public static byte[] compress(byte[] input) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Deflater def = new Deflater();
            def.setInput(input);
            def.finish();
            byte[] buffer = new byte[1024];
            while (!def.finished()) {
                int count = def.deflate(buffer);
                out.write(buffer, 0, count);
            }
            def.end();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decompress(byte[] input) {
        try {
            Inflater inf = new Inflater();
            inf.setInput(input);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!inf.finished()) {
                int count = inf.inflate(buffer);
                out.write(buffer, 0, count);
            }
            inf.end();
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new RuntimeException("Invalid object compression", e);
        }
    }

    public static String decode(byte[] data) {
        return new String(data);
    }

    // -------- Helper: check hex SHA --------
    static boolean looksLikeHex(String s) {
        if (s.length() < 4) return false;
        for (char c : s.toCharArray()) {
            if (!((c >= '0' && c <= '9') ||
                  (c >= 'a' && c <= 'f') ||
                  (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    // -------- Helper: convert hex string to byte array --------
    static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
