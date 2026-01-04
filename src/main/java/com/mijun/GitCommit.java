package com.mijun;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import com.mijun.libmijun.GitRepository;
import com.mijun.libmijun;
import java.util.*;

// Represents a Git commit object using Git’s KVLM (key-value-list-message) format
public class GitCommit extends GitObject {

    // Git object type identifier for commits
    public static final byte[] fmt =
            "commit".getBytes(StandardCharsets.US_ASCII);
    
    // Stores commit headers and message
    public LinkedHashMap<byte[], Object> kvlm;

    
    // Constructors
    
    // Creates an empty commit object
    public GitCommit() {
        this.kvlm = new LinkedHashMap<>();
    }

    // Creates a commit object by deserializing raw commit bytes
    public GitCommit(byte[] data) {
        deserialize(data);
    }
    
    // Git object type identifier
    @Override
    public byte[] type() {
        return fmt;
    }

    /* 
       Deserialize / Serialize
     */
    
    // Parses raw commit data into the KVLM structure
    @Override
    public void deserialize(byte[] data) {
        this.kvlm = kvlmParse(data, 0, null);
    }
    
    // Serializes the KVLM structure back into raw commit bytes
    @Override
    public byte[] serialize() {
        return kvlmSerialize(this.kvlm);
    }

    /* 
       KVLM PARSER
    */
    
    // Recursively parses commit headers and message into a LinkedHashMap
    private static LinkedHashMap<byte[], Object> kvlmParse(
            byte[] raw, int start, LinkedHashMap<byte[], Object> dct) {
        
        // Initializes the map on first call
        if (dct == null) dct = new LinkedHashMap<>();
        
        // Finds the first space and newline to detect header boundaries
        int spc = indexOf(raw, (byte) ' ', start);
        int nl  = indexOf(raw, (byte) '\n', start);

        // Base case: message starts here
        if (spc < 0 || nl < spc) {
            if (nl != start)
                throw new IllegalStateException("Malformed commit object");
            dct.put(null, Arrays.copyOfRange(raw, start + 1, raw.length));
            return dct;
        }

        byte[] key = Arrays.copyOfRange(raw, start, spc);

        int end = start;
        while (true) {
            end = indexOf(raw, (byte) '\n', end + 1);
            if (end + 1 >= raw.length || raw[end + 1] != (byte) ' ') break;
        }

        byte[] value = Arrays.copyOfRange(raw, spc + 1, end);
        value = replace(value, "\n ".getBytes(), "\n".getBytes());

        if (dct.containsKey(key)) {
            Object cur = dct.get(key);
            asByteList(cur).add(value);
        } else {
            dct.put(key, value);
        }

        // Recursively parses the remaining data
        return kvlmParse(raw, end + 1, dct);
    }

    // Serializes the KVLM structure back into raw commit bytes
    private static byte[] kvlmSerialize(
            LinkedHashMap<byte[], Object> kvlm) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            for (byte[] key : kvlm.keySet()) {
                if (key == null) continue;

                Object val = kvlm.get(key);
                List<byte[]> values =
                        (val instanceof List<?>)
                                ? asByteList(val)
                                : List.of((byte[]) val);

                for (byte[] v : values) {
                    out.write(key);
                    out.write(' ');
                    out.write(replace(v, "\n".getBytes(), "\n ".getBytes()));
                    out.write('\n');
                }
            }

            out.write('\n');
            out.write((byte[]) kvlm.get(null));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return out.toByteArray();
    }

    /* 
       LOG GRAPHVIZ
    */
    
    // Outputs the commit history in Graphviz format for visualization
    public static void logGraphviz(
            GitRepository repo, String sha, Set<String> seen) {

        if (seen.contains(sha)) return;
        seen.add(sha);
        
        // Reads the commit object from the repository
        GitCommit commit = (GitCommit)
                GitObject.object_read(repo, libmijun.fromHexString(sha));
        
        // Extracts and formats the commit message for labeling
        String message = new String(
                (byte[]) commit.kvlm.get(null),
                StandardCharsets.UTF_8
        ).strip()
         .replace("\\", "\\\\")
         .replace("\"", "\\\"");

        if (message.contains("\n")) {
            message = message.substring(0, message.indexOf("\n"));
        }

        // Prints the commit node
        System.out.println(
                "  c_" + sha + " [label=\"" +
                sha.substring(0, 7) + ": " + message + "\"]"
        );

        if (!commit.kvlm.containsKey("parent".getBytes())) return;

        Object parentsObj =
                commit.kvlm.get("parent".getBytes());

        List<byte[]> parents =
                (parentsObj instanceof List<?>)
                        ? asByteList(parentsObj)
                        : List.of((byte[]) parentsObj);

        for (byte[] p : parents) {
            String parentSha =
                    new String(p, StandardCharsets.US_ASCII);
            System.out.println(
                    "  c_" + sha + " -> c_" + parentSha + ";"
            );
            logGraphviz(repo, parentSha, seen);
        }
    }

    /* 
       TYPE-SAFE HELPER
    */
    
    // I was getting a type casting error - used this to control it as of now
    @SuppressWarnings("unchecked")
    private static List<byte[]> asByteList(Object o) {
        if (!(o instanceof List<?>))
            throw new IllegalStateException(
                    "Expected List<byte[]> but got " + o.getClass());

        List<?> raw = (List<?>) o;
        for (Object e : raw) {
            if (!(e instanceof byte[]))
                throw new IllegalStateException(
                        "KVLM list contains non-byte[]");
        }
        return (List<byte[]>) raw;
    }

    /* 
       Helper Methods
    */

    private static int indexOf(byte[] arr, byte b, int start) {
        for (int i = start; i < arr.length; i++)
            if (arr[i] == b) return i;
        return -1;
    }

    private static byte[] replace(
            byte[] src, byte[] target, byte[] repl) {

        return new String(src, StandardCharsets.UTF_8)
                .replace(new String(target), new String(repl))
                .getBytes(StandardCharsets.UTF_8);
    }
}