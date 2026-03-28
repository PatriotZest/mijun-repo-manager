package com.mijun;

import com.mijun.libmijun.GitRepository;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Represents a Git commit object using Git’s KVLM (key-value-list-message) format
 */
public class GitCommit extends GitObject {

    public static final byte[] fmt = "commit".getBytes(StandardCharsets.US_ASCII);

    // Stores commit headers and commit message
    public LinkedHashMap<byte[], Object> kvlm;

    // Constructors
    public GitCommit() {
        this.kvlm = new LinkedHashMap<>();
    }

    public GitCommit(byte[] data) {
        deserialize(data);
    }

    public GitCommit(Map<byte[], List<byte[]>> kvlm) {
        this.kvlm = new LinkedHashMap<>();
        for (Map.Entry<byte[], List<byte[]>> e : kvlm.entrySet()) {
            if (e.getValue().size() == 1) {
                this.kvlm.put(e.getKey(), e.getValue().get(0));
            } else {
                this.kvlm.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
    }
    @Override
    public byte[] type() {
        return fmt;
    }

    @Override
    public void deserialize(byte[] data) {
        this.kvlm = kvlmParse(data, 0, null);
    }

    @Override
    public byte[] serialize() {
        return kvlmSerialize(this.kvlm);
    }

    // -------- KVLM Parsing --------
    private static LinkedHashMap<byte[], Object> kvlmParse(byte[] raw, int start, LinkedHashMap<byte[], Object> dct) {
        if (dct == null) dct = new LinkedHashMap<>();

        int spc = indexOf(raw, (byte) ' ', start);
        int nl = indexOf(raw, (byte) '\n', start);

        // Base case: start of commit message
        if (spc < 0 || nl < spc) {
            if (start < raw.length) {
                byte[] msg = Arrays.copyOfRange(raw, start, raw.length);
                dct.put(null, msg);
            }
            return dct;
        }

        byte[] key = Arrays.copyOfRange(raw, start, spc);

        int end = nl;
        while (end + 1 < raw.length && raw[end + 1] == ' ') {
            end = indexOf(raw, (byte) '\n', end + 1);
        }

        byte[] value = Arrays.copyOfRange(raw, spc + 1, end);
        value = replace(value, "\n ".getBytes(StandardCharsets.UTF_8), "\n".getBytes(StandardCharsets.UTF_8));

        if (dct.containsKey(key)) {
            Object cur = dct.get(key);
            asByteList(cur).add(value);
        } else {
            dct.put(key, value);
        }

        return kvlmParse(raw, end + 1, dct);
    }

    private static byte[] kvlmSerialize(LinkedHashMap<byte[], Object> kvlm) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] key : kvlm.keySet()) {
                if (key == null) continue;

                Object val = kvlm.get(key);
                List<byte[]> values = (val instanceof List<?>) ? asByteList(val) : List.of((byte[]) val);

                for (byte[] v : values) {
                    out.write(key);
                    out.write(' ');
                    out.write(replace(v, "\n".getBytes(StandardCharsets.UTF_8),
                            "\n ".getBytes(StandardCharsets.UTF_8)));
                    out.write('\n');
                }
            }

            out.write('\n');
            if (kvlm.containsKey(null)) {
                out.write((byte[]) kvlm.get(null));
            }

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize KVLM", e);
        }
    }

    // -------- Graphviz log --------
    public static void logGraphviz(GitRepository repo, String sha, Set<String> seen) {
        if (seen.contains(sha)) return;
        seen.add(sha);

        GitCommit commit = (GitCommit) GitObject.object_read(repo, hexStringToBytes(sha));
        if (commit == null) return;

        String message = "";
        if (commit.kvlm.containsKey(null)) {
            message = new String((byte[]) commit.kvlm.get(null), StandardCharsets.UTF_8)
                    .strip().replace("\\", "\\\\").replace("\"", "\\\"");
            if (message.contains("\n")) message = message.substring(0, message.indexOf("\n"));
        }

        System.out.println("  c_" + sha + " [label=\"" + sha.substring(0, 7) + ": " + message + "\"]");

        if (!commit.kvlm.containsKey("parent".getBytes())) return;

        Object parentsObj = commit.kvlm.get("parent".getBytes());
        List<byte[]> parents = (parentsObj instanceof List<?>) ? asByteList(parentsObj) : List.of((byte[]) parentsObj);

        for (byte[] p : parents) {
            String parentSha = new String(p, StandardCharsets.US_ASCII);
            System.out.println("  c_" + sha + " -> c_" + parentSha + ";");
            logGraphviz(repo, parentSha, seen);
        }
    }

    // -------- Helper: type-safe List<byte[]> --------
    @SuppressWarnings("unchecked")
    private static List<byte[]> asByteList(Object o) {
        if (!(o instanceof List<?>)) throw new IllegalStateException("Expected List<byte[]> but got " + o.getClass());
        List<?> raw = (List<?>) o;
        for (Object e : raw) {
            if (!(e instanceof byte[])) throw new IllegalStateException("KVLM list contains non-byte[]");
        }
        return (List<byte[]>) raw;
    }

    // -------- Helper methods --------
    private static int indexOf(byte[] arr, byte b, int start) {
        for (int i = start; i < arr.length; i++) if (arr[i] == b) return i;
        return -1;
    }

    private static byte[] replace(byte[] src, byte[] target, byte[] repl) {
        return new String(src, StandardCharsets.UTF_8)
                .replace(new String(target, StandardCharsets.UTF_8), new String(repl, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    static public byte[] commit_create(
        GitRepository repo,
        byte[] tree,
        List<byte[]> parents,
        String message)
    {
        Map<byte[], List<byte[]>> kvlm = new LinkedHashMap<>();

        kvlm.put("tree".getBytes(), List.of(tree));

        if (parents != null) {
            for (byte[] p : parents) {
                kvlm.put("parent".getBytes(), List.of(p));
            }
        }
        String author = "Student <student@example.com> "
                + Instant.now().getEpochSecond()
                + " +0000";

        kvlm.put("author".getBytes(),
                List.of(author.getBytes(StandardCharsets.UTF_8)));
        kvlm.put("committer".getBytes(),
                List.of(author.getBytes(StandardCharsets.UTF_8)));

        kvlm.put(null,
                List.of(message.getBytes(StandardCharsets.UTF_8)));

        GitCommit commit = new GitCommit(kvlm);
        return GitObject.object_write(commit, repo);
    }
}
