package com.mijun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mijun.libmijun.GitRepository;

public class GitIgnore {
    ArrayList<wrapper> ret = new ArrayList<>();
    ArrayList<ArrayList<String>> absolute = null;
    Map<String, ArrayList<String>> scoped = new HashMap<>();
    class wrapper {
        String raw;
        boolean bool;

        wrapper(String raw, boolean bool) {
            this.raw = raw;
            this.bool = bool;
        }
    }

    GitIgnore(ArrayList<ArrayList<String>> absolute, Map<String, ArrayList<String>> scoped) {
        this.absolute = absolute;
        this.scoped = scoped;
    }




    public wrapper gitignore_parse1(String raw) {
        raw = raw.strip();

        if (raw.isBlank() || raw.charAt(0) == '#') {
            return null;
        }
        else if (raw.charAt(0) == '!') {
            return new wrapper(raw.substring(1), false);
        }
        else if(raw.charAt(0) == '\\') {
            return new wrapper(raw.substring(1), true);
        } else {
            return new wrapper(raw, true);
        }
    }

    public ArrayList<String> gitignore_parse(ArrayList<wrapper> lines) {
        String parsed = null;
        wrapper buff = null;
        ArrayList<String> ret = new ArrayList<>();
        for (var line: lines) {
            buff = gitignore_parse1(line.raw);
            parsed = buff.raw;
            if (buff.bool) {
                ret.add(buff.raw);
            }
        }
        return ret;
    }


    public GitIgnore gitignore_read(GitRepository repo) {
        try {
            GitIgnore ret = new GitIgnore(new ArrayList<ArrayList<String>>(), new HashMap<>());

            // local config
            Path repo_file = Paths.get(repo.gitDir.toString(), "info/exclude");
            if (Files.exists(repo_file)) {
                List<String> fileLines = Files.readAllLines(repo_file);
                ArrayList<wrapper> lines = new ArrayList<>();
                for (var fileLine: fileLines) {
                    lines.add(new wrapper(fileLine, true));
                }
                ret.absolute.add(gitignore_parse(lines));
            }

            // global config -- assisted
            String configHome;
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null && !xdgConfigHome.isEmpty()) {
                configHome = xdgConfigHome;
            } else {
                String home = System.getProperty("user.home");
                configHome = Paths.get(home, ".config").toString();
            }

            Path global_file = Paths.get(configHome, "mijun", "ignore");
            if (Files.exists(global_file)) {
                List<String> fileLines = Files.readAllLines(repo_file);
                ArrayList<wrapper> lines = new ArrayList<>();
                for (var fileLine: fileLines) {
                    lines.add(new wrapper(fileLine, true));
                }
                ret.absolute.add(gitignore_parse(lines));
            }


            GitIndex index = GitIndex.index_read(repo);
            
            for (var entry: index.entries) {
                String dir_name = null;
                if (entry.name == ".mijunignore" || entry.name.endsWith("/.mijunignore")) {
                    Path dir_name_p = Paths.get(entry.name);
                    if (dir_name_p.getParent() == null) {
                        dir_name = dir_name_p.toString();
                        dir_name = "";
                    }
                    GitObject contents = GitObject.object_read(repo, entry.sha.getBytes());
                    String text = new String(contents.data, StandardCharsets.UTF_8);
                    // no idea what is happening here
                    ArrayList<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R")));
                    ret.scoped.put(dir_name, lines);

                }
            }
            return ret;
        } catch (IOException e) {
            return null;
        }

        public Boolean check_ignore1() {
            Boolean result = null;
        }
    }




}
