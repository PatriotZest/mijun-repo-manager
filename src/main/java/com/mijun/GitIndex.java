package com.mijun;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.mijun.libmijun.GitRepository;

public class GitIndex {
    BigInteger version;
    ArrayList<GitIndexEntry> entries;
    
    GitIndex(BigInteger version, ArrayList<GitIndexEntry> entries) {
        if (entries.isEmpty()) {
            entries = new ArrayList<>();
        }
        this.version = version;
        this.entries = entries;
    }

    GitIndex() {
        this.version = new BigInteger("2");
        this.entries = null;
    }

    static public GitIndex index_read(GitRepository repo) {
        try {
            ArrayList<GitIndexEntry> entries = new ArrayList<>();
            Path index_file = libmijun.repoFile(repo, true, "index");

            if (Files.notExists(index_file)) {
                return new GitIndex();
            }
            
            byte[] raw = Files.readAllBytes(index_file);
            ByteArrayOutputStream outHeader = new ByteArrayOutputStream();
            for (int i = 0; i < 12; i++) {
                outHeader.write(raw[i]);
            }

            ByteArrayOutputStream outSignature = new ByteArrayOutputStream();
            for (int i = 0; i < 4; i++) {
                outSignature.write(raw[i]);
            }

            assert outSignature.toByteArray() == "DIRC".getBytes(): "Wrong dir cache";
            
            ByteArrayOutputStream outVersion = new ByteArrayOutputStream();
            for (int i = 4; i < 8; i++) {
                outVersion.write(raw[i]);
            }

            BigInteger version = new BigInteger(outVersion.toByteArray());
            assert version == BigInteger.valueOf(2): "mijun no bueno index file ver 2";

            ByteArrayOutputStream outCount = new ByteArrayOutputStream();
            for (int i = 8; i < 12; i++) {
                outCount.write(raw[i]);
            }
            BigInteger count = new BigInteger(outCount.toByteArray());

            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            for (int i = 12; i < raw.length; i++) {
                outContent.write(raw[i]);
            }
            byte[] content = outContent.toByteArray();
            int idx = 0;

            for (int i = 0; i < count.intValue(); i++) {
                // creation time
                int ctime_s = ByteBuffer.wrap(content, idx, 4).getInt();

                // creation time in nanoseconds
                int ctime_ns = ByteBuffer.wrap(content, idx + 4, 4).getInt();

                // modification time
                int mtime_s = ByteBuffer.wrap(content, idx + 8, 4).getInt();

                // mtime ns
                int mtime_ns = ByteBuffer.wrap(content, idx + 12, 4).getInt();

                // device id
                int dev = ByteBuffer.wrap(content, idx + 16, 4).getInt();

                // Inode
                int ino = ByteBuffer.wrap(content, idx + 20, 4).getInt();

                // ?? no idea what this is for
                int unused = ByteBuffer.wrap(content, idx + 16, 2).getInt();
                assert 0 == unused;
                
                int mode = ByteBuffer.wrap(content, idx + 26, 2).getInt();
                int mode_type = mode >> 12;
                assert mode_type == 0b1000 || mode_type == 0b1010 || mode_type == 0b1110;
                int mode_perms = mode & 0777;

                //userid
                int uid = ByteBuffer.wrap(content, idx + 28, 4).getInt();

                //groupid
                int gid = ByteBuffer.wrap(content, idx + 32, 4).getInt();

                //size
                int fsize = ByteBuffer.wrap(content, idx + 36, 4).getInt();

                //sha object id
                String sha = String.format("%040x", ByteBuffer.wrap(content, idx + 40, 20).getInt());

                // ignored flag_stage
                int flags = ByteBuffer.wrap(content, idx + 60, 2).getInt();

                // parse flags wth is happening here
                boolean flag_assume_valid = (flags & 0b1000000000000000) != 0;
                boolean flag_extended = (flags & 0b1000000000000000) != 0;
                assert !flag_extended;
                int flag_stage = flags & 0b0011000000000000;

                int name_length = flags & 0b0000111111111111;

                idx += 62;
                byte[] raw_name = null;
                if (name_length < 0xFFF) {
                    assert content[idx + name_length] == 0x00;
                    ByteArrayOutputStream outRawName = new ByteArrayOutputStream();
                    for (int j = idx; j < idx + name_length; j++) {
                        outRawName.write(content[i]);
                    }
                    idx += name_length + 1;
                } else {
                    // appaarently if path > 0xFFF bytes then it works but breaks if its mode_perms
                    int null_idx = 0;
                    for (i = 0; i < (idx + 0xFFF); i++) {
                        if (content[i] == 0) {
                            null_idx = i;
                            break;
                        }
                    }
                    ByteArrayOutputStream outRawName = new ByteArrayOutputStream();
                    for (i = idx; i < null_idx; i++) {
                        outRawName.write(content[i]);
                    }
                    raw_name = outRawName.toByteArray();
                    idx = null_idx + 1;
                }

                String name = new String(raw_name, StandardCharsets.UTF_8);
                idx = (int) (8 * Math.ceil(idx / 8));
                entries.add(new GitIndexEntry(Instant.ofEpochSecond(ctime_s, ctime_ns), Instant.ofEpochSecond(mtime_s, mtime_ns), dev, ino, mode_type, mode_perms, uid, gid, fsize, sha, flag_assume_valid, flag_stage, name));
            }
            return new GitIndex(version, entries);
        } catch (IOException e) {
            System.out.println("no bueno io exception");
            return null;
        }   
    }
}


class GitIndexEntry {
    Instant ctime = null;
    Instant mtime = null;
    int dev = 0;
    int ino = 0;
    int mode_type;
    int mode_perms;
    int uid;
    int gid;
    int fsize;
    String sha;
    boolean flag_assume_valid;
    int flag_stage;
    String name;
    
    GitIndexEntry(Instant ctime, Instant mtime, int dev, int ino, int mode_type, int mode_perms, int uid, int gid, int fsize, String sha, boolean flag_assume_valid, int flag_stage, String name) {
        this.ctime = ctime;
        this.mtime = mtime;
        this.dev = dev;
        this.ino = ino;
        this.mode_type = mode_type;
        this.mode_perms = mode_perms;
        this.uid = uid;
        this.gid = gid;
        this.fsize = fsize;
        this.sha = sha;
        this.flag_assume_valid = flag_assume_valid;
        this.flag_stage = flag_stage;
        this.name = name;
    }
}


