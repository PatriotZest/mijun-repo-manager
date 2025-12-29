package com.mijun;

// argparse
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

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
// {Misha please figure this out} hmm

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
        Subparser initParser = subparsers.addParser("init")
                .help("Make a new repository");
        initParser.addArgument("path")
                .nargs("?")
                .setDefault(".")
                .help("dir to init, default: current dir");
        

        try {
            Namespace ns = parser.parseArgs(args);
            String command = ns.getString("command");

            if (command == null) {
                parser.printHelp();
                System.exit(1);
            }

            switch (command) {
                case "init":
                    System.out.println("Hello!");
                    break;
                default:
                    System.err.println("Bad command: " + command);
                    System.exit(1);
            }
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
