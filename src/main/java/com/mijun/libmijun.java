package com.mijun;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        
        // log parser
        Subparser logParser = subparsers.addParser("log").help("Display commit history");
        logParser.addArgument("commit")
                .nargs("?")
                .setDefault("HEAD")
                .help("Commit to start at (default: HEAD)");

        Subparser treeParser = subparsers.addParser("ls-tree").help("Pretty-print a tree object");
        treeParser.addArgument("-r")
                .dest("recursive")
                .action(net.sourceforge.argparse4j.impl.Arguments.storeTrue())
                .help("recursve");
        treeParser.addArgument("tree")
                .help("A tree like object or sum shi");

        Subparser checkoutParser = subparsers.addParser("checkout").help("Checkout a commmit inside of a directory");
        checkoutParser.addArgument("commit")
                .help("Commit or tree to checkout");
        checkoutParser.addArgument("path")
                .help("The empty dir to checkout on");
        
        /** 
        Subparser addParser = subparsers.addParser("ls-files").help("Lists all stage files");
        addParser.addArgument("--verbose")
                .action(net.sourceforge.argparse4j.impl.Arguments.storeTrue())
                .help("Show everything");
        **/ 

        // Parser for add command
        Subparser addParser = subparsers.addParser("add")
                .help("Add file contents to the index");
        addParser.addArgument("paths").nargs("+");

        // Parser for show-ref command
        Subparser showRefParser =
        subparsers.addParser("show-ref")
                  .help("List references");

        // Parser for tag command
        Subparser tagParser = subparsers.addParser("tag")
        .help("List and create tags");
        
        tagParser.addArgument("-a")
        .dest("annotated")
        .action(Arguments.storeTrue());
        
        tagParser.addArgument("name").nargs("?");
        
        tagParser.addArgument("object")
        .nargs("?")
        .setDefault("HEAD");
        
        // Parser for rev-parse command
        Subparser revParser = subparsers.addParser("rev-parse");
        revParser.addArgument("--wyag-type")
                .dest("type")
                .choices("blob","commit","tree","tag");
        revParser.addArgument("name");

        Subparser checkIgnoreParser = subparsers.addParser("check-ignore").help("Check ignore paths");
        checkIgnoreParser.addArgument("path")
                .nargs("+")
                .help("Paths to check");
        
        Subparser statusParser = subparsers.addParser("status").help("Show working tree status");

        Subparser writeTreeParser = subparsers.addParser("write-tree")
        .help("Create a tree object from the index");

        Subparser commitParser = subparsers.addParser("commit")
        .help("Record changes to the repository");
        commitParser.addArgument("-m")
        .required(true)
        .help("Commit message");



        Subparser addParser = subparsers.addParser("ls-files").help("Lists all stage files");
        addParser.addArgument("--verbose")
                .action(net.sourceforge.argparse4j.impl.Arguments.storeTrue())
                .help("Show everything");

        Subparser checkIgnoreParser = subparsers.addParser("check-ignore").help("Check ignore paths");
        checkIgnoreParser.addArgument("path")
                .nargs("+")
                .help("Paths to check");
        
        Subparser statusParser = subparsers.addParser("status").help("Show working tree status");
        
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
                
                // {Arjun added some exception handling here -- running into NPE issues here }
                case "hash-object":
                    byte[] type = ns.getString("type").getBytes();
                    Path path = Paths.get(ns.getString("path")).toAbsolutePath();
                    repo = null;

                    if (ns.getBoolean("write")) {
                        try {
                            repo = repoFind();
                        } catch (IOException e) {
                            System.err.println("Error: Could not find repository to write object into.");
                            System.exit(1);
                        }
                    }
                    byte[] sha = object_hash(path, type, repo);

                    if (sha == null) {
                        System.err.println("Error: Could not hash object.");
                        System.exit(1);
                    }
                    System.out.println(toHex(sha));
                    break;
                
                case "log":
                    repo = repoFind();
                    String start = ns.getString("commit");

                    String commitShaStr;

                    if (start.equals("HEAD")) {
                        Path head = repoPath(repo, "HEAD");
                        String ref = Files.readString(head).trim();
                        if (ref.startsWith("ref: ")) {
                            String refPath = ref.substring(5);
                            Path refFullPath = repoPath(repo, refPath);
                            commitShaStr = Files.readString(refFullPath).strip();
                        } else {
                            commitShaStr = ref;
                        }
                    } else {
                        commitShaStr = start;
                    }

                    System.out.println("digraph log {");
                    System.out.println("node [shape=box]");
                    GitCommit.logGraphviz(repo, commitShaStr, new java.util.HashSet<>());
                    System.out.println("}");
                    break;

                case "checkout":
                    repo = repoFind();

                    GitObject obj = GitObject.object_read(repo, GitObject.object_find(repo, ns.getString("commit"), null,true));
                
                    // making a change here {Arjun}
                    if (Arrays.equals(obj.type(), "commit".getBytes())) {
                        GitCommit cobj = (GitCommit) obj;
                        
                        byte[] treeKey = "tree".getBytes();
                        byte[] treeSha = null;

                        // ai-generated no idea if works or not
                        for (var e: cobj.kvlm.entrySet()) {
                            if (Arrays.equals(e.getKey(), treeKey)) {
                                treeSha = (byte[]) e.getValue();
                            }
                        }
                        cobj = (GitCommit) GitObject.object_read(repo, treeSha);
                    }

                    if (Files.exists(Paths.get(ns.getString("path")))) {
                        if (!Files.isDirectory(Paths.get(ns.getString("path")))) {
                            throw new IllegalArgumentException("YOU OUTTA UR MIND - not a dir");
                        }
                        File dir = new File(ns.getString("path"));
                        String[] files = dir.list();
                        if (!(files != null && files.length == 0)) {
                            throw new IllegalArgumentException("YOU OUTTA UR MIND - not empty dir");
                        }
                    } else {
                        Files.createDirectories(Paths.get(ns.getString("path")));
                    }
                    GitTree tobj = (GitTree) obj;   
                    GitTree.tree_checkout(repo, tobj, Paths.get(ns.getString("path")).toRealPath());       
                    break;

                case "ls-tree":
                    repo = repoFind();
                    GitTree.ls_tree(repo, ns.getString("tree").getBytes(), ns.getBoolean("recursive"), null);
                    break;
                
                case "show-ref":
                    repo = repoFind();
                    Map<String, Object> refs = GitRef.refList(repo, null);
                    showRef(refs, "refs");
                    break;

                case "tag":
                    repo = repoFind();
                    if (ns.getString("name") == null) {
                        refs = GitRef.refList(repo, null);
                        showRef((Map<String, Object>) refs.get("tags"), "refs/tags");
                    } else {
                        tagCreate(repo, ns.getString("name"), ns.getString("object"),
                         ns.getBoolean("annotated"));
                    }
                    break;
                    
                case "rev-parse":
                    repo = repoFind();
                    byte[] revType = null;
                    if (ns.getString("type") != null) {
                        revType = ns.getString("type").getBytes();
                    }
                    byte[] revSha = GitObject.object_find(repo,
                            ns.getString("name"), revType, true);
                    System.out.println(toHex(revSha));
                    break;

                    GitTree.ls_tree(repo,ns.getString("tree").getBytes() , ns.getBoolean("recursive"), null);
                    break;
                
                case "ls-files":
                    repo = repoFind();
                    GitIndex index = GitIndex.index_read(repo);
                    if (ns.getBoolean("--verbose")) {
                        System.out.println("print something here");
                    }
                    // TODO: add shi
                    for (var e: index.entries) {
                        System.out.println(e.name);
                        if (ns.getBoolean("--verbose")) {
                           Map<Integer, String> entryTypeMap = Map.of(
                            0b1000, "regular file",
                            0b1010, "symlink",
                            0b1110, "git link"
                           );
                           String entryType = entryTypeMap.get(e.mode_type);
                           System.out.println(entryType + " with perms: " + Integer.toOctalString(e.mode_perms));
                           System.out.println(" on blob: " + e.sha);
                           System.out.println(" device: " + e.dev + " inode: " + e.ino);
                           System.out.println(" user: ");
                           System.out.println("flags: stage = " + e.flag_stage);
                        }
                    }
                    break;
                
                case "check-ignore":
                    repo = repoFind();
                    GitIgnore rules = new GitIgnore();
                    rules = rules.gitignore_read(repo);
                    List<String> paths = ns.getList("path");
                    for (var pathStr: paths) {
                        path = Paths.get(pathStr);
                        if (rules.check_ignore(rules, path)) {
                            System.out.println(pathStr);
                        }
                    }
                    break;
                
                case "status":
                    repo = repoFind();
                    index = GitIndex.index_read(repo);

                    break;
                
                case "add":
                    repo = repoFind();
                    GitIndex.add(repo, ns.getList("paths"));
                    break;

                case "write-tree":
                    repo = repoFind();
                    index = GitIndex.index_read(repo);
                    byte[] treeSha = GitTree.tree_from_index(repo, index);
                    System.out.println(toHex(treeSha));
                    break;

                case "commit":
                    repo = repoFind();
                    GitIndex index2 = GitIndex.index_read(repo);
                    byte[] tree = GitTree.tree_from_index(repo, index2);

                    byte[] parent = GitObject.object_find(repo, "HEAD", null, false);

                    byte[] commitSha = GitCommit.commit_create(
                        repo,
                        tree,
                        parent == null ? List.of() : List.of(parent),
                        ns.getString("m")
                    );

                    GitRef.refCreate(repo, "heads/master", commitSha);
                    System.out.println(toHex(commitSha));
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
        Path path = startDir.toAbsolutePath();

        if (Files.isDirectory(path.resolve(".mijun"))) {
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
        return repoFind(Paths.get(System.getProperty("user.dir")).toAbsolutePath(), true);
    }
    
    static void cat_file(GitRepository repo, byte[] objByte, byte[] fmt) {
        try {
            byte[] sha = GitObject.object_find(repo, new String(objByte), fmt, true);
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
            
            switch(new String(fmt)) {
                case "blob":
                    obj = new GitBlob(data);
                    break;

                case "commit":
                    obj = new GitCommit(data);
                    break;
                
                case "tree":
                    obj = new GitTree();
                    break;
                
                case "tag":
                    obj = new GitTag();
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

    static byte[] fromHex(byte[] hex) {
        int len = hex.length;
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i+=2) {
            out[i / 2] = (byte) ((Character.digit(hex[i], 16) << 4) + (Character.digit(hex[i + 1], 16)));
        }
        return out;
    }

    static byte[] fromHexString(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] =
              (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    // recursive function to show refs
    static void showRef(Map<String, Object> refs, String prefix) {
    for (var e : refs.entrySet()) {
        if (e.getValue() instanceof byte[]) {
            System.out.println(
                toHex((byte[]) e.getValue()) + " " +
                prefix + "/" + e.getKey());
        } else {
            showRef((Map<String, Object>) e.getValue(),
                    prefix + "/" + e.getKey());
            }
        }
    }
    
    // create tag
    static void tagCreate(GitRepository repo, String name, String ref, boolean annotated) throws IOException {

        byte[] sha = GitObject.object_find(
            repo, ref, null, true);

        if (annotated) {
            GitTag tag = new GitTag();
            tag.kvlm = new LinkedHashMap<>();
            tag.kvlm.put("object".getBytes(), sha);
            tag.kvlm.put("type".getBytes(), "commit".getBytes());
            tag.kvlm.put("tag".getBytes(), name.getBytes());
            tag.kvlm.put("tagger".getBytes(),
                "mijun <mijun@example.com>".getBytes());
            tag.kvlm.put(null,
                "Annotated tag\n".getBytes());

            byte[] tagSha = GitObject.object_write(tag, repo);
            GitRef.refCreate(repo, "tags/" + name, tagSha);
        } else {
            GitRef.refCreate(repo, "tags/" + name, sha);
        }
    }
    public static String branch_get_active(GitRepository repo) {
        //String head = Files.readString("HEAD");

        //if (head.startsWith("ref: refs/heads/")) {
          //  return head.substring(16, -1);
        //} else {
          //  return false;
        //}
        return null;
    }

    public static void cmd_status_branch(GitRepository repo) {
        String branch = branch_get_active(repo);
        if (branch != null) {
            System.out.println("On branch" + branch);
        } else {
            System.out.println("HEAD detached at" + GitObject.object_find(repo, null, null, false));
        }
    }

    public static void cmd_status_head_index(GitRepository repo, GitIndex index) {
        System.out.println("Changes to be committed: ");

        Map<Path, byte[]> head = GitTree.tree_to_dict(repo, "HEAD".getBytes(), "");
        for (var entry: index.entries) {
            if (head.containsKey(Paths.get(entry.name))) {
                if (head.get(Paths.get(entry.name)) != entry.sha.getBytes()) {
                    System.out.println(" modified:" + entry.name);
                }
                head.remove(Paths.get(entry.name));
            } else {
                System.out.println(" added: " +  entry.name);
            }
        }

        for (var entry: head.keySet()) {
            System.out.println(" deleted: " + entry);
        }
    }

    public void cmd_status_index_worktree(GitRepository repo, GitIndex index) {
        try {
            System.out.println("Changes not staged for commit:");
            GitIgnore ignore = new GitIgnore(null, null);
            ignore = ignore.gitignore_read(repo);

            String gitdir_prefix = repo.gitDir.toString() + File.separator;

            List<String> all_files = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(repo.workTree)) {
                for (Path fullPath: stream.filter(Files::isRegularFile).toList()) {
                    if (fullPath.startsWith(repo.gitDir)) {
                        continue;
                    }

                    Path relPath = repo.workTree.relativize(fullPath);
                    all_files.add(relPath.toString());
                }
            } catch (IOException e) {
                System.out.println("err no bueno");
            }

            for (var entry: index.entries) {
                Path full_path = Path.of(repo.workTree.toString(), entry.name);

                if (Files.notExists(full_path)) {
                    System.out.println(" deleted: " + entry.name);
                } else {
                    BasicFileAttributes st = Files.readAttributes(full_path, BasicFileAttributes.class);
                    long ctime_ns = entry.ctime.getEpochSecond() * 1_000_000_000L + entry.ctime.getNano();
                    long mtime_ns = entry.mtime.getEpochSecond() * 1_000_000_000L + entry.mtime.getNano();

                    Instant ctimeInstant = st.creationTime().toInstant();
                    Instant mtimeInstant = st.lastModifiedTime().toInstant();

                    long fileCtimeNs = ctimeInstant.getEpochSecond() * 1_000_000_000L + ctimeInstant.getNano();
                    long fileMtimeNs = mtimeInstant.getEpochSecond() * 1_000_000_000L + mtimeInstant.getNano();

                    if (fileCtimeNs != ctime_ns || fileMtimeNs != mtime_ns) {
                    byte[] fd = Files.readAllBytes(full_path);
                    byte[] new_sha = object_hash(full_path, "blob".getBytes(), null);
                    boolean same = entry.sha.equals(new_sha);

                    if (!same) {
                        System.out.println(" modified:" + entry.name);
                    }
                    }
                }
                if (all_files.contains(entry.name)) {
                    all_files.remove(entry.name);
                }
            }
            System.out.println();
            System.out.println("Untracked files:");

            for (var f: all_files) {
                if (ignore.check_ignore(ignore, Path.of(f))) {
                    System.out.println("" + f);
                }
            }
        } catch (IOException e) {
            System.out.println("no bueno");
        }
    }

    /* 
       TEMPORARY INITIAL COMMIT CREATION
    */

    public static void cmd_status_branch(GitRepository repo) {
        String branch = branch_get_active(repo);
        if (branch != null) {
            System.out.println("On branch" + branch);
        } else {
            System.out.println("HEAD detached at" + GitObject.object_find(repo, null, null, false));
        }
    }

    public static void cmd_status_head_index(GitRepository repo, GitIndex index) {
        System.out.println("Changes to be committed: ");

        Map<Path, byte[]> head = GitTree.tree_to_dict(repo, "HEAD".getBytes(), "");
        for (var entry: index.entries) {
            if (head.containsKey(Paths.get(entry.name))) {
                if (head.get(Paths.get(entry.name)) != entry.sha.getBytes()) {
                    System.out.println(" modified:" + entry.name);
                }
                head.remove(Paths.get(entry.name));
            } else {
                System.out.println(" added: " +  entry.name);
            }
        }

        for (var entry: head.keySet()) {
            System.out.println(" deleted: " + entry);
        }
    }

    public void cmd_status_index_worktree(GitRepository repo, GitIndex index) {
        try {
            System.out.println("Changes not staged for commit:");
            GitIgnore ignore = new GitIgnore(null, null);
            ignore = ignore.gitignore_read(repo);

            String gitdir_prefix = repo.gitDir.toString() + File.separator;

            List<String> all_files = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(repo.workTree)) {
                for (Path fullPath: stream.filter(Files::isRegularFile).toList()) {
                    if (fullPath.startsWith(repo.gitDir)) {
                        continue;
                    }

                    Path relPath = repo.workTree.relativize(fullPath);
                    all_files.add(relPath.toString());
                }
            } catch (IOException e) {
                System.out.println("err no bueno");
            }

            for (var entry: index.entries) {
                Path full_path = Path.of(repo.workTree.toString(), entry.name);

                if (Files.notExists(full_path)) {
                    System.out.println(" deleted: " + entry.name);
                } else {
                    BasicFileAttributes st = Files.readAttributes(full_path, BasicFileAttributes.class);
                    long ctime_ns = entry.ctime.getEpochSecond() * 1_000_000_000L + entry.ctime.getNano();
                    long mtime_ns = entry.mtime.getEpochSecond() * 1_000_000_000L + entry.mtime.getNano();

                    Instant ctimeInstant = st.creationTime().toInstant();
                    Instant mtimeInstant = st.lastModifiedTime().toInstant();

                    long fileCtimeNs = ctimeInstant.getEpochSecond() * 1_000_000_000L + ctimeInstant.getNano();
                    long fileMtimeNs = mtimeInstant.getEpochSecond() * 1_000_000_000L + mtimeInstant.getNano();

                    if (fileCtimeNs != ctime_ns || fileMtimeNs != mtime_ns) {
                    byte[] fd = Files.readAllBytes(full_path);
                    byte[] new_sha = object_hash(full_path, "blob".getBytes(), null);
                    boolean same = entry.sha.equals(new_sha);

                    if (!same) {
                        System.out.println(" modified:" + entry.name);
                    }
                    }
                }
                if (all_files.contains(entry.name)) {
                    all_files.remove(entry.name);
                }
            }
            System.out.println();
            System.out.println("Untracked files:");

            for (var f: all_files) {
                if (ignore.check_ignore(ignore, Path.of(f))) {
                    System.out.println("" + f);
                }
            }
        } catch (IOException e) {
            System.out.println("no bueno");
        }
    }
}