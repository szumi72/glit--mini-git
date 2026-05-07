package glit.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import glit.cli.Call;

public class GlitController {

    // powloka bash-owa samodzielnie rozwija, wtedy tylko kropke potrzebujemy; dla Powershella się wykona dla *
    // PathMatcher dopasowuje symbole wieloznaczne znane z powłoki, np. . i *
    public static List<Path> expandWildcard(String pattern) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir"));
        List<Path> result = new ArrayList<>();

        if (pattern.equals(".")) {
            pattern = "**";
        }
        PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + pattern);

        try (Stream<Path> stream = Files.walk(dir)) {
            stream
                    .filter(Files::isRegularFile)
                    .map(dir::relativize)
                    .filter(p -> matcher.matches(p))
                    .forEach(result::add);
        }
        // } else {
        // PathMatcher matcher = FileSystems.getDefault()
        //         .getPathMatcher("glob:" + pattern);
        // try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
        //     for (Path entry : stream) {
        //         if (matcher.matches(entry.getFileName())) {
        //             result.add(entry);
        //         }
        //     }
        // }
        // }
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
                    System.out.println("File name not given. \nUsage: glit add <file1> <file2> ...");
                    return null;
                }
                for (int i = INDEX_OF_FUNCTION + 1; i < args.length; i++) {
                    // flag 
                    if(args[i].contains("-")){
                        System.out.println("glit add doesn't have flag " + args[i]);
                        return null;
                    }
                    // file or files with wildcards not exists
                    if (!Files.exists(Path.of(args[i]))) {
                        List<Path> files = expandWildcard(args[i]);
                        if(files == null || files.size()<1){
                            System.out.println("Cannot find file " + args[i] + ".");
                            return null;
                        }else{
                            arguments.add(files);
                        }                       
                    }else{ arguments.add(Path.of(args[i])); }
                    // DOROBIC OBSLUGE DIRECTORY!!!!!!!!!!!!!!!!!
                }
                return new Call(functionName, null, arguments);
            }
            case "commit" -> {
                // message
                if (args.length <= INDEX_OF_FUNCTION + 2) {
                    System.out.println("Commit name not given. \nUsage: glit commit -m <your_commit_name>");
                    return null;
                }
                // -m flag
                if (!args[INDEX_OF_FUNCTION + 1].equals("-m")) {
                    System.out.println("Commit -m option is required. \nUsage: glit commit -m <your_commit_name>");
                    return null;
                }else{
                    flags.add("m");
                }
                // weird flag OBSLUZYC!!!!!!!!!!!!!!!!!!!!!!!!
                // if(args[i].contains("-")){
                //     System.out.println("glit add doesn't have flag " + args[i]);
                //     return null;
                // }
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
                // arguments
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    String errBlock = """
                        Branch name not given.
                        Usage: glit checkout <your_branch_name>
                        For creating new branch use: glit checkout -b <your_new_branch_name>""";
                    System.out.println(errBlock);
                    return null;
                }
                boolean creatingNewBranch = args[INDEX_OF_FUNCTION + 1].equals("-b");
                // new branch name
                if (creatingNewBranch && args.length <= INDEX_OF_FUNCTION + 2) {
                    String errBlock = """
                        Branch name not given.
                        Usage: glit checkout <your_branch_name>
                        For creating new branch use: glit checkout -b <your_new_branch_name>""";
                    System.out.println(errBlock);
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
                    }System.out.println();
                    return null;
                } else if (creatingNewBranch && branchList.contains(branchName)) {
                    System.out.println("Cannot use name \"" + branchName + "\" - it has been used. Branches currently in use:");
                    for (String el : branchList) {
                        System.out.print(el + "  ");
                    }System.out.println();
                    return null;
                }
                if(creatingNewBranch){
                    flags.add("b");
                    String newBranchName = args[INDEX_OF_FUNCTION + 2];
                    args[INDEX_OF_FUNCTION + 2] = newBranchName.replaceAll("\\s+", "_");
                    if (!newBranchName.equals(args[INDEX_OF_FUNCTION + 2])) {
                        System.out.println("New branch's name changed to: " + args[INDEX_OF_FUNCTION + 2]);
                    }
                    arguments.add(args[INDEX_OF_FUNCTION + 2]);
                }else{
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
        // List<Path> files = expandWildcard("*");
        // for (Path elem : files) {
        //     System.out.println(elem);
        // }
        System.out.println(cliCall);
        // Wywołaj odpowiednią metodę
        switch (cliCall.getFunction()) {
            case "init" -> {
                System.out.println("init executed");
                // glit.service.Repository.init();
            }
            case "add" ->
                System.out.println("add executed");
            case "commit" ->
                System.out.println("commit executed -> " + args[1]);
            case "checkout" ->
                System.out.println("checkout executed");
            case "merge" ->
                System.out.println("merge executed");
        }
    }
}
/*
Jak jest potrzebny 1 argument to czy sprawdzać czy użytkownik nie wpisał 2+ argumentów?

Co sprawdza:
add:
    czy podano 1+ plików
    czy pliki istnieją
    czy mają prawo odczytu
    czy nie są katalogiem

    do dorobienia:
        sprawdzanie czy już nie jest śledzone -> to już chyba w samej metodzie add()
        
    do dyskusji;
        czy można dodać katalog
        czy pliki nie muszą być wykonywalne
        obsługa symboli wieloznacznych?
        .glitignore -> robimy go w ogóle?; na jakim etapie go obsłużyć
        obsługa niestandardowych znaków - / + itd.

commit:
    czy jest wiadomość - jeśli tak to zmienia białe znaki na "_"

    do dyskusji:
        obsługa niestandardowych znaków - / + itd.

checkout:
    czy jest podany argument 
    czy nie jest wpisana nazwa aktualnie używanego brancha
    czy branch wpisany jest zapisany w liście branchy

    do dorobienia:
        w RefManager -> getCurrentBranch() oraz getBranches(); potem odkomentować i przetestować
    
    do dyskusji:
        tworzenie nowych branchy - w jaki sposób? checkout -b br2 czy jakoś inaczej?
    
merge:
    czy jest podany argument
    czy wpisany branch nie jest aktualnie używany (bo mergujemy się do tego aktualnie używanego)
    czy wpisany branch jest zapisany w liście branchy

    do dorobienia:
        to samo co w checkout

 */
