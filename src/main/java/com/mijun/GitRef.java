package com.mijun;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.mijun.libmijun.GitRepository;

public class GitRef {

    // 7.1 ref_resolve 
    // resolves a ref to its SHA-1 hash
    public static byte[] refResolve(GitRepository repo, String ref) throws IOException {

        Path path = libmijun.repoPath(repo, ref);

        if (!Files.exists(path)) {
            return null;
        }

        String data = Files.readString(path).trim();

        if (data.startsWith("ref: ")) {
            return refResolve(repo, data.substring(5));
        }

        return libmijun.fromHexString(data);
    }

    // 7.1 ref_list
    // lists all refs in the repository
    public static Map<String, Object> refList(GitRepository repo, Path path) throws IOException {

        if (path == null) {
            path = libmijun.repoPath(repo, "refs");
        }

        Map<String, Object> ret = new TreeMap<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    ret.put(name, refList(repo, p));
                } else {
                    ret.put(name, refResolve(repo,
                            repoRelative(repo, p)));
                }
            }
        }
        return ret;
    }

    // helper to get repo-relative path
    private static String repoRelative(GitRepository repo, Path p) {
        return repo.gitDir.relativize(p).toString();
    }

    // 7.4 ref_create
    // creates or updates a ref to point to a given SHA-1 hash
    public static void refCreate(GitRepository repo, String refName, byte[] sha) throws IOException {

        Path p = libmijun.repoFile(repo, true, "refs", refName);
        Files.writeString(p, libmijun.toHex(sha) + "\n");
    }
}
