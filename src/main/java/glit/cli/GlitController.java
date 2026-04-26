// package glit.cli;

import java.nio.file.Files;
import java.nio.file.Path;

public class GlitController {

    public static boolean validateCommandLineArgs(String[] args) {
        if (args.length < 1) {
            System.out.println("You need to add function name. \nExample of usage: glit add <file1> <file2> ...");
            return false;
        }
        int INDEX_OF_FUNCTION = 0;
        String functionName = args[INDEX_OF_FUNCTION];
        switch (functionName) {
            case "init" -> {
                return true;
            }
            case "add" -> {
                // jest nazwa chociaz 1 pliku
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("File name not given. \nUsage: glit add <file1> <file2> ...");
                    return false;
                }
                // czy plik istnieje, jest readable, jest katalogiem
                for (int i = INDEX_OF_FUNCTION + 1; i < args.length; i++) {
                    if (!Files.exists(Path.of(args[i]))) {
                        System.out.println("Cannot add. There's no file \"" + args[i] + "\".");
                        return false;
                    }
                    if (!Files.isReadable(Path.of(args[i]))) {
                        System.out.println("Cannot add. File \"" + args[i] + "\" is not readable.");
                        return false;
                    }
                    if (Files.isDirectory(Path.of(args[i]))) {
                        System.out.println("Cannot add. \"" + args[i] + "\" is a directory.");
                        return false;
                    }
                }
                return true;
            }
            case "commit" -> {
                // jest wiadomosc
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("Commit name not given. \nUsage: glit commit <your_commit_name>");
                    return false;
                }
                String commitName = args[INDEX_OF_FUNCTION + 1];
                args[INDEX_OF_FUNCTION + 1] = commitName.replaceAll("\\s+", "_");
                if (!commitName.equals(args[INDEX_OF_FUNCTION + 1])) {
                    System.out.println("Commit name changed to: " + args[INDEX_OF_FUNCTION + 1]);
                }
                return true;
            }
            case "checkout" -> {
                // jest wpisana nazwa brancha
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    String errBlock = """
                        Branch name not given.
                        Usage: glit checkout <your_branch_name>
                        For creating new branch use: glit branch <your_new_branch_name>
                        """;
                    System.out.println(errBlock);
                    return false;
                }
                /*
                // aktualnie używany branch
                String branchName = args[INDEX_OF_FUNCTION + 1];
                if (branchName == RefManager.getActualBranch()) {
                System.out.println("Branch " + branchName + " is currently being used");
                return false;
                }
                // nazwa jest w systemie
                List<String> branchList = RefManager.getBranches();
                if (!branchList.contains(branchName)) {
                System.out.println("Branch \"" + branchName + "\" not found. Available branches:");
                for (String el : branchList) {
                System.out.print(el + " ");
                }
                }
                 */
                return true;
            }
            case "merge" -> {
                // jest nazwa brancha
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("Branch name not given. \nUsage: glit merge <branch_to_be_merged_with_your_current>");
                    return false;
                }
                /*
                // nazwa jest w systemie
                String branchName = args[INDEX_OF_FUNCTION + 1];
                List<String> branchList = RefManager.getBranches();
                if (!branchList.contains(branchName)) {
                    System.out.println("Branch \"" + branchName + "\" not found. Available branches:");
                    for (String el : branchList) {
                        System.out.print(el + " ");
                    }
                    return false;
                }
                 */
                return true;
            }

            default -> {
                System.out.println("Glit doesn't have a function called " + functionName + ".");
                return false;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (!validateCommandLineArgs(args)) {
            return;
        }
        // Wywołaj odpowiednią metodę
        switch (args[0]) {
            case "init" ->
                System.out.println("init executed");
            // glit.service.Repository.init();
            case "add" ->
                System.out.println("add executed");
            case "commit" ->
                System.out.println("commit executed -> " + args[1]);
            // do przetestowania!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            case "checkout" ->
                System.out.println("checkout executed");
            case "merge" ->
                System.out.println("merge executed");
        }
    }
}
