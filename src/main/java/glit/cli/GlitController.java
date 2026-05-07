// package glit.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

    public static boolean validateCommandLineArgs(String[] args) {
        if (args.length < 1) {
            String s = """
            You need to add function name.
            Available functions:
            init  add  commit  checkout  merge
            Example of usage: glit <function> [OTPIONS] [ARGS...]""";
            System.out.println(s);
            return false;
        }
        int INDEX_OF_FUNCTION = 0;
        String functionName = args[INDEX_OF_FUNCTION];
        switch (functionName) {
            case "init" -> {
                // jest nazwa chociaz 1 pliku
                if (args.length > INDEX_OF_FUNCTION + 1) {
                    System.out.println("Unnecessary arguments. \nUsage: glit init");
                    return false;
                }
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
                    // if (Files.isDirectory(Path.of(args[i]))) {
                    //     System.out.println("Cannot add. \"" + args[i] + "\" is a directory.");
                    //     return false;
                    // }
                }
                return true;
            }
            case "commit" -> {
                // jest wiadomosc
                if (args.length <= INDEX_OF_FUNCTION + 2) {
                    System.out.println("Commit name not given. \nUsage: glit commit -m <your_commit_name>");
                    return false;
                }
                if (!args[INDEX_OF_FUNCTION + 1].equals("-m")) {
                    System.out.println("Commit -m option is required. \nUsage: glit commit -m <your_commit_name>");
                    return false;
                }
                String commitName = args[INDEX_OF_FUNCTION + 2];
                args[INDEX_OF_FUNCTION + 1] = commitName.replaceAll("\\s+", "_");
                if (!commitName.equals(args[INDEX_OF_FUNCTION + 1])) {
                    System.out.println("Commit name changed to: " + args[INDEX_OF_FUNCTION + 1]);
                }
                return true;
            }
            case "checkout" -> {
                // jest wpisana nazwa brancha lub -b nowybranch
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    String errBlock = """
                        Branch name not given.
                        Usage: glit checkout <your_branch_name>
                        For creating new branch use: glit checkout -b <your_new_branch_name>""";
                    System.out.println(errBlock);
                    return false;
                }
                boolean creatingNewBranch = args[INDEX_OF_FUNCTION + 1].equals("-b");
                if (creatingNewBranch && args.length <= INDEX_OF_FUNCTION + 2) {
                    String errBlock = """
                        Branch name not given.
                        Usage: glit checkout <your_branch_name>
                        For creating new branch use: glit checkout -b <your_new_branch_name>""";
                    System.out.println(errBlock);
                    return false;
                }

                // aktualnie używany branch
                String branchName = creatingNewBranch ? args[INDEX_OF_FUNCTION + 2] : args[INDEX_OF_FUNCTION + 1];
                // if (branchName.equals(RefManager.getCurrentBranch()())) {
                if (branchName.equals("main")) {
                    System.out.println("Branch " + branchName + " is currently being used");
                    return false;
                }
                // nazwa jest w systemie
                // List<String> branchList = RefManager.getBranches();
                List<String> branchList = List.of("feature", "feature/init", "feature/parse", "main");
                if (!creatingNewBranch && !branchList.contains(branchName)) {
                    System.out.println("Branch \"" + branchName + "\" not found. Available branches:");
                    for (String el : branchList) {
                        System.out.print(el + "\t");
                    }
                } else if (creatingNewBranch && branchList.contains(branchName)) {
                    System.out.println("Cannot use name \"" + branchName + "\" - it has been used. Branches currently in use:");
                    for (String el : branchList) {
                        System.out.print(el + "\t");
                    }
                }

                return true;
            }
            case "merge" -> {
                // jest nazwa brancha
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("Branch name not given. \nUsage: glit merge <branch_to_be_merged_with_your_current>");
                    return false;
                }
                // aktualnie używany branch
                String branchName = args[INDEX_OF_FUNCTION + 1];
                // if (branchName.equals(RefManager.getCurrentBranch()())) {
                if (branchName.equals("main")) {
                    System.out.println("Cannot merge from branch " + branchName + " - it is currently being used.");
                    return false;
                }
                // nazwa jest w systemie
                // List<String> branchList = RefManager.getBranches();
                List<String> branchList = List.of("feature", "feature/init", "feature/parse", "main");
                if (!branchList.contains(branchName)) {
                    System.out.println("Branch \"" + branchName + "\" not found. Available branches:");
                    for (String el : branchList) {
                        System.out.print(el + "\t");
                    }
                    System.out.println();
                    return false;
                }

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
        List<Path> files = expandWildcard("*");
        for (Path elem : files) {
            System.out.println(elem);
        }

        // Wywołaj odpowiednią metodę
        switch (args[0]) {
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
