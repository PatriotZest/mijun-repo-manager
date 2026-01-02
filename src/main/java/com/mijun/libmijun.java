package com.mijun;

// argparse
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import net.sourceforge.argparse4j.impl.Arguments;

// configparser
import org.ini4j.*;

// datetime
import java.time.*;

// unix shi
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.GroupPrincipal;

// fnmatch
import java.nio.file.*;

// hashlib
import java.security.MessageDigest;

// math
import java.math.*;

// os
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

// re
import java.util.regex.*;

// zlib
import java.util.zip.*;

public class libmijun {

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("mijun").build()
                .defaultHelp(true)
                .description("Mijun repo manager");

        Subparsers subparsers = parser.addSubparsers()
                .dest("command")
                .help("command help");

        // init command
        Subparser initParser = subparsers.addParser("init").help("Create a new repository");
        initParser.addArgument("path")
                .nargs("?")
                .setDefault(System.getProperty("user.dir"))
                .help("Directory to initialize, default: current directory");

        // find command
        // "find" command to locate repository root
        Subparser findParser = subparsers.addParser("find").help("Find root of Mijun repository");

        // cat-file parser
        Subparser catParser = subparsers.addParser("cat-file").help("Raw content of repository objects");
        catParser.addArgument("type")
                .metavar("type")
                .choices("blob", "commit", "tag", "tree")
                .help("Specify the type");
        catParser.addArgument("object")
                .metavar("object")
                .help("The object to display");
        
        Subparser hashParser = subparsers.addParser("hash-object").help("Compute object id and optionally create a blob");
        hashParser.addArgument("-t")
                .metavar("type")
                .dest("type")
                .choices("blob", "commit", "tag", "tree")
                .setDefault("blob")
                .help("Specify the type");
        hashParser.addArgument("-w")
                .dest("write")
                .action(net.sourceforge.argparse4j.impl.Arguments.storeTrue())
                .help("write object into the db");
        hashParser.addArgument("path")
                .help("Read object from path");
        try {
            Namespace ns = parser.parseArgs(args);
            String command = ns.getString("command");

            if (command == null) {
                parser.printHelp();
                System.exit(1);
            }
            
            switch (command) {
                case "init":
                    Path p = Paths.get(ns.getString("path")).toAbsolutePath();
                    GitRepository repo = repoCreate(p);
                    System.out.println("Initialized empty Mijun repository in " + p);
                    break;

                case "find":
                    GitRepository foundRepo = repoFind();
                    System.out.println("Repository found at: " + foundRepo.workTree.toAbsolutePath());
                    break;

                case "cat-file":
                    repo = repoFind();
                    byte[] objByte = ns.getString("object").getBytes();
                    byte[] fmt = ns.getString("type").getBytes();
                    cat_file(repo, objByte, fmt);
                    break;
                
                case "hash-object":
                    byte[] type = ns.getString("type").getBytes();
                    if (ns.getBoolean("write")) {
                        repo = repoFind();
                    } else {
                        repo = null;
                    }
                    Path path = ns.get("path");
                    byte[] sha = object_hash(path, type, repo);
                    System.out.println(sha);
                    break;

                default:
                    System.err.println("Bad command: " + command);
                    System.exit(1);
            }
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /* 
       GIT REPOSITORY CLASS
    */

    // Represents a repository with worktree, gitdir, and config
    static class GitRepository {
        Path workTree; // path to working directory
        Path gitDir; // path to .git directory
        Wini config; // configuration parsed from .git/config

        GitRepository(Path path, boolean force) throws IOException {
            this.workTree = path.toAbsolutePath();
            this.gitDir = path.resolve(".mijun");

            if (!force && !Files.isDirectory(gitDir)) {
                throw new RuntimeException("Not a Mijun repository: " + path);
            }
            
            // Load config if it exists
            Path configPath = gitDir.resolve("config");
            if (Files.exists(configPath)) {
                this.config = new Wini(configPath.toFile());
            } else if (!force) {
                throw new RuntimeException("Configuration file missing: " + configPath);
            }
            
             // Validate repository format version
            if (!force && config != null) {
                int version = config.get("core", "repositoryformatversion", int.class);
                if (version != 0) {
                    throw new RuntimeException("Unsupported repositoryformatversion: " + version);
                }
            }
        }
    }

    /* 
       PATH HELPERS
    */

    // Build a path inside the .git directory
    static Path repoPath(GitRepository repo, String... parts) {
        Path p = repo.gitDir;
        for (String part : parts) {
            p = p.resolve(part);
        }
        return p;
    }
    
    // Build or create a directory inside the repository
    static Path repoDir(GitRepository repo, boolean mkdir, String... parts) throws IOException {
        Path path = repoPath(repo, parts);

        if (Files.exists(path)) {
            if (Files.isDirectory(path)) return path;
            throw new RuntimeException("Not a directory: " + path);
        }

        if (mkdir) {
            Files.createDirectories(path);
            return path;
        }

        return null;
    }
    
    // Build or create a file inside the repository
    static Path repoFile(GitRepository repo, boolean mkdir, String... parts) throws IOException {
        Path dir = repoDir(repo, mkdir, java.util.Arrays.copyOf(parts, parts.length - 1));
        if (dir != null) return repoPath(repo, parts);
        return null;
    }
    
    /* 
       REPO CREATE (INIT)
    */

    // Initialize a new repository at given path
    static GitRepository repoCreate(Path path) throws IOException {
        // Ensure base directory exists
        if (!Files.exists(path)) Files.createDirectories(path);

        GitRepository repo = new GitRepository(path, true);

        // Ensure .git exists
        if (!Files.exists(repo.gitDir)) Files.createDirectories(repo.gitDir);

        // Create standard git folders
        repoDir(repo, true, "branches");
        repoDir(repo, true, "objects");
        repoDir(repo, true, "refs", "heads");
        repoDir(repo, true, "refs", "tags");

        // Create description and HEAD -- these are files
        Files.writeString(repoFile(repo, true, "description"),
                "Unnamed repository; edit this file 'description' to name the repository.\n");
        Files.writeString(repoFile(repo, true, "HEAD"),
                "ref: refs/heads/master\n");

        Path configPath = repo.gitDir.resolve("config");
        if (!Files.exists(configPath)) Files.createFile(configPath);

        Wini cfg = new Wini(configPath.toFile());
        cfg.put("core", "repositoryformatversion", "0");
        cfg.put("core", "filemode", "false");
        cfg.put("core", "bare", "false");
        cfg.store();

        return repo;
    }

    /* 
       REPO FIND
    */
    static GitRepository repoFind(Path startDir, boolean required) throws IOException {
        Path path = startDir.toRealPath();

        if (Files.isDirectory(path.resolve(".git"))) {
            return new GitRepository(path, false);
        }

        Path parent = path.getParent();
        if (parent == null || parent.equals(path)) {
            if (required) throw new RuntimeException("No Mijun repository found.");
            return null;
        }

        return repoFind(parent, required);
    }

    // find repository root from current directory
    // { Done -Arjun }
    static GitRepository repoFind() throws IOException {
        return repoFind(Paths.get(System.getProperty("user.dir")), true);
    }
    static void cat_file(GitRepository repo, byte[] objByte, byte[] fmt) {
        try {
            byte[] sha = GitObject.object_find(repo, objByte, fmt, true);
            GitObject obj = GitObject.object_read(repo, sha);

            System.out.write(obj.serialize());
        } catch (IOException e) {
            System.out.println("no bueno io");
        }
    }

    static byte[] object_hash(Path path, byte[] fmt, GitRepository repo) {
        try {
            GitObject obj = null;
            byte[] data = Files.readAllBytes(path);
            // {Misha you need to add commit here :3}
            switch(new String(fmt)) {
                case "blob":
                    obj = new GitBlob(data);
                    break;
                default:
                    System.out.println("some shi is 100% going wrong");
                    break;
            }
            return GitObject.object_write(obj, repo);
        } catch (IOException e) {
            System.out.println("no bueno io error");
        }
        return null;
    }

    // this shi helps in converting byte[] to string (in hex format which git uses) so that i can use substring fyi
    static String toHex(byte[] sha) {
        StringBuilder sb = new StringBuilder();
        for (byte b: sha) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
