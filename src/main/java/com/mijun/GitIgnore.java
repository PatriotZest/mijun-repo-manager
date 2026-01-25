package com.mijun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;
import java.nio.file.FileSystems;

import com.mijun.libmijun.GitRepository;

public class GitIgnore {
    ArrayList<ArrayList<Map.Entry<String, Boolean>>> absolute = new ArrayList<>();
    Map<String, ArrayList<Map.Entry<String, Boolean>>> scoped = new HashMap<>();
    
    class Wrapper {
        String raw;
        boolean value;

        Wrapper(String raw, boolean value) {
            this.raw = raw;
            this.value = value;
        }
    }

    GitIgnore(ArrayList<ArrayList<Map.Entry<String, Boolean>>> absolute, 
              Map<String, ArrayList<Map.Entry<String, Boolean>>> scoped) {
        this.absolute = absolute;
        this.scoped = scoped;
    }

    GitIgnore() {
        this.absolute = new ArrayList<>();
        this.scoped = new HashMap<>();
    }

    public Wrapper gitignore_parse1(String raw) {
        raw = raw.strip();

        if (raw.isBlank() || raw.charAt(0) == '#') {
            return null;
        }
        else if (raw.charAt(0) == '!') {
            return new Wrapper(raw.substring(1), false);
        }
        else if(raw.charAt(0) == '\\') {
            return new Wrapper(raw.substring(1), true);
        } else {
            return new Wrapper(raw, true);
        }
    }

    // FIXED: Return List<Map.Entry<String, Boolean>> instead of ArrayList<String>
    public ArrayList<Map.Entry<String, Boolean>> gitignore_parse(List<String> lines) {
        ArrayList<Map.Entry<String, Boolean>> ret = new ArrayList<>();
        
        for (String line : lines) {
            Wrapper parsed = gitignore_parse1(line);
            if (parsed != null) {
                // Create Map.Entry with pattern and value
                ret.add(new AbstractMap.SimpleEntry<>(parsed.raw, parsed.value));
            }
        }
        return ret;
    }

    public GitIgnore gitignore_read(GitRepository repo) {
        try {
            GitIgnore ret = new GitIgnore();

            // Local config
            Path repo_file = Paths.get(repo.gitDir.toString(), "info", "exclude");
            if (Files.exists(repo_file)) {
                List<String> fileLines = Files.readAllLines(repo_file);
                ArrayList<Map.Entry<String, Boolean>> parsed = gitignore_parse(fileLines);
                ret.absolute.add(parsed);
            }

            // Global config
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
                // FIXED: Read from global_file, not repo_file
                List<String> fileLines = Files.readAllLines(global_file);
                ArrayList<Map.Entry<String, Boolean>> parsed = gitignore_parse(fileLines);
                ret.absolute.add(parsed);
            }

            // Read .mijunignore files from index
            GitIndex index = GitIndex.index_read(repo);
            
            for (var entry : index.entries) {
                if (entry.name.equals(".mijunignore") || entry.name.endsWith("/.mijunignore")) {
                    String dir_name;
                    Path namePath = Paths.get(entry.name);
                    
                    if (namePath.getParent() == null) {
                        dir_name = "";
                    } else {
                        dir_name = namePath.getParent().toString();
                    }
                    
                    GitObject contents = GitObject.object_read(repo, entry.sha.getBytes());
                    String text = new String(contents.data, StandardCharsets.UTF_8);
                    
                    // Split into lines and parse
                    List<String> lines = Arrays.asList(text.split("\\R"));
                    ArrayList<Map.Entry<String, Boolean>> parsed = gitignore_parse(lines);
                    ret.scoped.put(dir_name, parsed);
                }
            }
            return ret;
        } catch (IOException e) {
            System.err.println("Error reading gitignore: " + e.getMessage());
            return null;
        }
    }

    public Boolean checkIgnore1(List<Map.Entry<String, Boolean>> rules, Path path) {
        Boolean result = null;
        
        for (Map.Entry<String, Boolean> entry : rules) {
            String pattern = entry.getKey();
            Boolean value = entry.getValue();
            
            PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + pattern);
            
            if (matcher.matches(path)) {
                result = value;
            }
        }
        
        return result;
    }

    public Boolean checkIgnoreScoped(
        Map<String, ArrayList<Map.Entry<String, Boolean>>> rules, 
        Path path
    ) {
        Path parent = path.getParent();
        
        while (parent != null) {
            String parentStr = parent.toString();
            
            if (rules.containsKey(parentStr)) {
                ArrayList<Map.Entry<String, Boolean>> ruleset = rules.get(parentStr);
                Boolean result = checkIgnore1(ruleset, path);
                if (result != null) {
                    return result;
                }
            }
            
            if (parentStr.isEmpty()) {
                break;
            }
            parent = parent.getParent();
        }
        
        return null;
    }

    public Boolean checkIgnoreAbsolute(
        List<ArrayList<Map.Entry<String, Boolean>>> rules, 
        Path path
    ) {
        for (List<Map.Entry<String, Boolean>> ruleset : rules) {
            Boolean result = checkIgnore1(ruleset, path);
            if (result != null) {
                return result;
            }
        }
        return false;
    }

    public Boolean check_ignore(GitIgnore rules, Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("This function requires path to be relative to the repository's root");
        }

        Boolean result = checkIgnoreScoped(rules.scoped, path);
        if (result != null) {
            return result;
        }

        return checkIgnoreAbsolute(rules.absolute, path);
    }
}