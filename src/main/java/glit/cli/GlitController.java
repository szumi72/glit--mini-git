package glit.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import glit.service.Repository;

public class GlitController {

    
    public static List<Path> expandWildcard(String pattern) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir"));
        List<Path> result = new ArrayList<>();

        if (pattern.equals(".") || pattern.equals("./")) {
            pattern = "**";
        }
        
        try {
            PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + pattern);
            try (Stream<Path> stream = Files.walk(dir)) {
                stream
                        .filter(Files::isRegularFile)
                        .map(dir::relativize)
                        .filter(p -> matcher.matches(p))
                        .forEach(result::add);
            }
        }catch(Exception e){ System.out.println(pattern + " not recognized");}

        return result;
    }

    public static Call validateAndParseCommandLineArgs(String[] args) throws IOException {
        if (args.length < 1) {
            String s = """
            You need to add function name.
            Available functions:
            init  add  commit  checkout  merge
            Example of usage: glit <function> [FLAGS] [ARGS...]""";
            System.out.println(s);
            return null;
        }
        int INDEX_OF_FUNCTION = 0;
        String functionName = args[INDEX_OF_FUNCTION];
        List<String> flags = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        switch (functionName) {
            case "init" -> {
                // unnecessary arguments
                if (args.length > INDEX_OF_FUNCTION + 1) {
                    System.out.println("Unnecessary arguments. \nUsage: glit init");
                    return null;
                }
                return new Call(functionName, null, null);
            }

            case "add" -> {
                // no arguments
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("File name not given. \nUsage: glit add <file1> [file2] ...");
                    return null;
                }
                for (int i = INDEX_OF_FUNCTION + 1; i < args.length; i++) {
                    // flag 
                    if (args[i].contains("-")) {
                        System.out.println("glit add doesn't have flag " + args[i]);
                        return null;
                    }
                    // file or files with wildcards not exists
                    Path file = Path.of(args[i]);
                    if (!Files.exists(file) || Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                        // System.out.println("Expanding wildcard... ");
                        List<Path> files = expandWildcard(args[i]);
                        if (files == null || files.size() < 1) {
                            System.out.println("Cannot find file " + args[i]);
                            return null;
                        } else {
                            arguments.addAll(files);
                        }
                    } else {
                        arguments.add(Path.of(args[i]));
                    }
                }
                return new Call(functionName, null, arguments);
            }
            case "commit" -> {
                String usageMessage = "Usage: glit commit -m <your_commit_name>";
                // message
                if (args.length <= INDEX_OF_FUNCTION + 2) {
                    System.out.println("Commit name not given. \n" + usageMessage);
                    return null;
                }
                // -m flag
                if (!args[INDEX_OF_FUNCTION + 1].equals("-m")) {
                    System.out.println("Commit -m option is required. \n" + usageMessage);
                    return null;
                } else {
                    flags.add("m");
                }
                // unnecessary arguments
                if (args.length > INDEX_OF_FUNCTION + 3) {
                    System.out.println("Unnecessary arguments. \n" + usageMessage);
                    return null;
                }
                // weird flag
                if (args[INDEX_OF_FUNCTION + 2].startsWith("-")) {
                    System.out.println("glit commit doesn't have flag " + args[INDEX_OF_FUNCTION + 2] + "\n" + usageMessage);
                    return null;
                }
                // 
                String commitName = args[INDEX_OF_FUNCTION + 2];
                args[INDEX_OF_FUNCTION + 2] = commitName.replaceAll("\\s+", "_");
                if (!commitName.equals(args[INDEX_OF_FUNCTION + 2])) {
                    System.out.println("Commit name changed to: " + args[INDEX_OF_FUNCTION + 2]);
                }
                arguments.add(args[INDEX_OF_FUNCTION + 2]);
                return new Call(functionName, flags, arguments);
            }
            case "checkout" -> {
                String usageMessage = """
                    Usage: glit checkout <branch_name>
                    For creating new branch use: glit checkout -b <new_branch_name>""";

                // unnecessary arguments
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("Wrong number of arguments. \n" + usageMessage);
                    return null;
                }
                boolean creatingNewBranch = args[INDEX_OF_FUNCTION + 1].equals("-b");
                if ((creatingNewBranch && args.length != INDEX_OF_FUNCTION + 3) || (!creatingNewBranch && args.length != INDEX_OF_FUNCTION + 2)) {
                    System.out.println("Wrong number of arguments. \n" + usageMessage);
                    return null;
                }

                // currently used branch
                String branchName = creatingNewBranch ? args[INDEX_OF_FUNCTION + 2] : args[INDEX_OF_FUNCTION + 1];
                // if (branchName.equals(RefManager.getCurrentBranch()())) {
                if (branchName.equals("main")) {
                    System.out.println("Branch " + branchName + " is currently being used");
                    return null;
                }
                // name in system
                // List<String> branchList = RefManager.getBranches();
                List<String> branchList = List.of("feature", "feature/init", "feature/parse", "main");
                if (!creatingNewBranch && !branchList.contains(branchName)) {
                    System.out.println("Branch \"" + branchName + "\" not found. Available branches:");
                    for (String el : branchList) {
                        System.out.print(el + "  ");
                    }
                    System.out.println();
                    return null;
                } else if (creatingNewBranch && branchList.contains(branchName)) {
                    System.out.println("Cannot use name \"" + branchName + "\" - it has been used. Used names: ");
                    for (String el : branchList) {
                        System.out.print(el + "  ");
                    }
                    System.out.println();
                    return null;
                }
                if (creatingNewBranch) {
                    flags.add("b");
                    String newBranchName = args[INDEX_OF_FUNCTION + 2];
                    args[INDEX_OF_FUNCTION + 2] = newBranchName.replaceAll("\\s+", "_");
                    if (!newBranchName.equals(args[INDEX_OF_FUNCTION + 2])) {
                        System.out.println("New branch's name changed to: " + args[INDEX_OF_FUNCTION + 2]);
                    }
                    arguments.add(args[INDEX_OF_FUNCTION + 2]);
                } else {
                    arguments.add(args[INDEX_OF_FUNCTION + 1]);
                }
                return new Call(functionName, flags, arguments);
            }
            case "merge" -> {
                // number of arguments
                if (args.length != INDEX_OF_FUNCTION + 2) {
                    System.out.println("Wrong arguments. \nUsage: glit merge <branch_to_be_merged_with_your_current>");
                    return null;
                }
                // branch currently in use
                String branchName = args[INDEX_OF_FUNCTION + 1];
                // if (branchName.equals(RefManager.getCurrentBranch()())) {
                if (branchName.equals("main")) {
                    System.out.println("Cannot merge from branch " + branchName + " - it is currently in use.");
                    return null;
                }
                // name in system
                // List<String> branchList = RefManager.getBranches();
                List<String> branchList = List.of("feature", "feature/init", "feature/parse", "main");
                if (!branchList.contains(branchName)) {
                    System.out.println("Branch \"" + branchName + "\" not found. Available branches:");
                    for (String el : branchList) {
                        System.out.print(el + "  ");
                    }
                    System.out.println();
                    return null;
                }
                arguments.add(branchName);
                return new Call(functionName, null, arguments);
            }
            case "cat-file" -> {
                if (args.length < INDEX_OF_FUNCTION + 2) {
                    System.out.println("Hash not given. \nUsage: glit cat-file <hash>");
                    return null;
                }
                //arguments
                arguments.add(args[INDEX_OF_FUNCTION + 1]);
                return new Call(functionName,null,arguments);
            }
            case "status" ->{
                return new Call(functionName,null,null);
            }
            case "log"->{
                return new Call(functionName,null,null);
            }

            default -> {
                System.out.println("Glit doesn't have a function called " + functionName + ".");
                return null;
            }
        }
    }

    public static void main(String[] args) throws Exception {
                Call cliCall = validateAndParseCommandLineArgs(args);
        if (cliCall == null) {
            return;
        }

        System.out.println(cliCall);
        // call proper method
        switch (cliCall.getFunction()) {
            case "init" -> {
                System.out.println("executing init... \n");
                Repository.init();
            }
            case "add" ->{
                System.out.println("executing add... \n");
                Repository.add(cliCall);
            }
            case "commit" ->
                System.out.println("commit executed");
            case "checkout" ->
                System.out.println("checkout executed");
            case "merge" ->
                System.out.println("merge executed");
            case "cat-file" ->{
                System.out.println("cat-file executed");
                Repository.catFile(cliCall);
            }
            case "status" ->{
                System.out.println("status executed");
                Repository.status();
            }
            case "log" ->{
                System.out.println("log executed");
                Repository.log();
            }
            
        }
    }
}

