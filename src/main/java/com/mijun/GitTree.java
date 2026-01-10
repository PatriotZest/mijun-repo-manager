package com.mijun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.mijun.libmijun.GitRepository;

public class GitTree extends GitObject {
    public static final byte[] fmt = "tree".getBytes(); 
    ArrayList<GitTreeLeaf> gitList;

    public GitTree() {
        this.gitList = new ArrayList<>();
    }

    @Override
    public byte[] serialize() {
        return tree_serialize(gitList);
    }

    @Override
    public void deserialize(byte[] data) {
        this.gitList = tree_parse(data);
    }

    @Override
    public byte[] type() {
        return fmt;
    }

    public class h_GitTreeLeaf {
        int y = 0;
        byte[] mode = null;
        Path path = null;
        byte[] sha = null;

        h_GitTreeLeaf(int y, byte[] mode, Path path, byte[] sha) {
            this.y = y;
            this.mode = mode;
            this.path = path;
            this.sha = sha;
        }
    }

    public class GitTreeLeaf {
        byte[] mode = null;
        Path path = null;
        byte[] sha = null;

        GitTreeLeaf(byte[] mode, Path path, byte[] sha) {
            this.mode = mode;
            this.path = path;
            this.sha = sha;
        }
    }



    /*
        Functions for tree 
    */
    public ArrayList<GitTreeLeaf> tree_parse(byte[] raw) {
        int pos = 0;
        int max = raw.length;
        ArrayList<GitTreeLeaf> ret = new ArrayList<>();

        while (pos < max) {
            h_GitTreeLeaf val = tree_parse_one(raw, pos);
            pos = val.y;
            GitTreeLeaf data = new GitTreeLeaf(val.mode,val.path,val.sha);
            ret.add(data);
        }
        return ret;
    }

    public h_GitTreeLeaf tree_parse_one(byte[] raw, int start) {
        int x = 0;
        int y = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream pathByte = new ByteArrayOutputStream();
        ByteArrayOutputStream shaByte = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == ' ') {
                x = i;
                break;
            }
        }

        if (x - start == 5 || x - start == 6) {
            for (int i = start; i < x; i++) {
                out.write(raw[i]);
            }
            if (out.size() == 5) {
                out.write(0);
            }

            for (int i = 0; i < raw.length; i++) {
                if (raw[i] == 0) {
                    y = i;
                    break;
                }
            }

            for (int i = x + 1; i < y; i++) {
                pathByte.write(raw[i]);
            }

            String pathString = new String(pathByte.toByteArray(), StandardCharsets.UTF_8);
            Path path = Paths.get(pathString);

            for (int i = (y + 1); i < (y + 21); i++) {
                shaByte.write(raw[i]);
            }

            byte[] mode = out.toByteArray();
            byte[] sha = shaByte.toByteArray();
            return new h_GitTreeLeaf(y + 21, mode, path, sha);
        }
        return null;
    }

    public Path tree_leaf_sort_key(GitTreeLeaf leaf) {
        if (leaf.mode[0] == '0' && leaf.mode[1] == '1') {
            return leaf.path;
        } else {
            String c_leaf = leaf.path.toString();
            c_leaf = c_leaf + "/";
            leaf.path = Paths.get(c_leaf);
            return leaf.path;
        }
    }

    public byte[] tree_serialize(ArrayList<GitTreeLeaf> obj) {
        try {
            obj.sort((a, b) ->
                tree_leaf_sort_key(a).compareTo(tree_leaf_sort_key(b))
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for(var i : obj) {
                out.write(i.mode);
                out.write(' ');
                out.write(i.path.toString().getBytes());
                out.write(0);
                String shaString = libmijun.toHex(i.sha);
                BigInteger sha = new BigInteger(shaString, 16);
                out.write(sha.toByteArray());
            }
            return out.toByteArray();
        } catch (IOException e) {
            System.out.println("No bueno io error");
        }
        return null;
    }

    static public void ls_tree(GitRepository repo, byte[] ref, boolean recursive, String prefix) {
        byte[] sha = object_find(repo, ref, fmt, true);
        GitTree obj = (GitTree) object_read(repo, sha);
        String type = null;
        for (var item: obj.gitList) {
            if (item.mode.length == 5) {
                String modeString = new String(item.mode);
                type = modeString.substring(0,1);
            } else {
                String modeString = new String(item.mode);
                type = modeString.substring(0,2);
            }

            switch(type) {
                case "04": type = "tree"; break;
                case "10": type = "blob"; break;
                case "12": type = "blob"; break;   // symlink? tf is that
                case "16": type = "commit"; break;
            }

            if(!(recursive && type == "tree")) {
                StringBuilder output = new StringBuilder();
                for (int i = 0; i < (6 - item.mode.length); i++) {
                    output.append("0");
                }
                output.append(libmijun.toHex(item.mode));
                output.append(" ");
                output.append(type);
                output.append(" ");
                output.append(item.sha);
                output.append("\t");
                output.append(Path.of(prefix).resolve(item.path));
                System.out.println(output);
            } else {
                ls_tree(repo, item.sha, recursive, prefix + item.path.toString());
            }
        }
    }
    static void tree_checkout(GitRepository repo, GitTree tree, Path path) {
        try {
            for (var item: tree.gitList) {
                GitObject obj = (GitTree) object_read(repo, item.sha);
                String dest = path.toString() + item.path.toString();

                if (obj.type() == "tree".getBytes()) {
                    Files.createDirectory(Paths.get(dest));
                    GitTree cobj = (GitTree) obj;
                    tree_checkout(repo, cobj, Paths.get(dest));
                } else if (obj.type() == "blob".getBytes()) {
                    GitBlob bobj = (GitBlob) obj;
                    Files.write(Paths.get(dest), obj.data);
                }
            }
        } catch (IOException e) {
            System.out.println("no bueno io exception");
        }
    }

}
