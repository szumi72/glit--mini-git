package glit.cli;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import glit.service.Repository;

/**
 * <p>The central controller and command router for the Glit Version Control System CLI.</p>
 *
 * <p>This class serves as the main entry point of the application. It captures command-line
 * arguments, executes strict structural and state validations, expands file system wildcards,
 * maps the inputs into executable {@link Call} contexts, and dispatches them to the underlying
 * {@link Repository} service layer.</p>
 */
public class GlitController {

    /**
     * The resolved root path of the active Glit repository.
     */
    private final static Path repositoryPath = Repository.whereIsRepo();

    /**
     * <p>Expands shell-like wildcard/glob patterns into a list of concrete file paths.</p>
     *
     * <p>This method normalizes shorthand patterns and performs file system traversal:</p>
     * <ul>
     * <li>Transforms current directory shortcuts ({@code .} or {@code ./}) into a recursive wildcard ({@code **}).</li>
     * <li>Appends a recursive wildcard ({@code /**}) if the provided pattern points directly to an existing directory.</li>
     * <li>Walks the file tree from the current working directory, filtering out non-regular files and matching them against the glob engine.</li>
     * <li>Resolves all discovered paths relative to the repository root.</li>
     * </ul>
     *
     * @param pattern the glob/wildcard pattern string provided by the user (e.g., "*.txt", "src/")
     * @return a {@link List} of {@link Path} objects matching the pattern, relative to the repository root
     * @throws IOException if an error occurs during file system walking or path resolution
     */
    public static List<Path> expandWildcard(String pattern) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir"));
        Path diffPath = repositoryPath.relativize(dir);
        List<Path> result = new ArrayList<>();

        if (pattern.equals(".") || pattern.equals("./")) {
            pattern = "**";
        }

        if(Files.isDirectory(Path.of(pattern), LinkOption.NOFOLLOW_LINKS)){
            pattern = pattern + "/**";
        }

        try {
            PathMatcher matcher = FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern);
            try (Stream<Path> stream = Files.walk(dir)) {
                stream
                        .filter(Files::isRegularFile)
                        .map(dir::relativize)
                        .filter(p -> matcher.matches(p))
                        .map(p -> diffPath.resolve(p))
                        .forEach(result::add);
            }
        } catch (Exception e) {
            System.out.println(pattern + " not recognized");
        }

        return result;
    }

    /**
     * <p>Validates and parses raw command-line arguments into a structured execution token.</p>
     *
     * <p>The parsing process enforces several stateful and syntactic constraints:</p>
     * <ul>
     * <li>Ensures a command function name is explicitly requested.</li>
     * <li>Blocks all operations (except {@code init}) if no valid {@code .glit} repository context exists.</li>
     * <li>Evaluates argument structures individually per command ({@code init}, {@code add}, {@code commit}, {@code checkout}, {@code merge}, {@code cat-file}, {@code status}, {@code log}, {@code branch}).</li>
     * <li>Handles flag abstractions (e.g., verifying {@code -m} for commits or {@code -b} for branch creations).</li>
     * <li>Performs automatic sanitation, such as replacing spaces with underscores in new branch names.</li>
     * </ul>
     *
     * <p>If any validation checks fail, descriptive usage context is printed to standard output,
     * and the routine aborts gracefully by returning {@code null}.</p>
     *
     * @param args the raw array of command-line arguments passed from the application entry point
     * @return a fully populated {@link Call} object ready for execution dispatch, or {@code null} if validation fails
     * @throws IOException if an error occurs during wildcard resolution or repository configuration queries
     */
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

        // no repo
        if(repositoryPath == null && !args[INDEX_OF_FUNCTION].equals("init")){
            System.out.println("No glit repository found. Create one with: glit init");
            return null;
        }

        String functionName = args[INDEX_OF_FUNCTION];
        List<String> flags = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        switch (functionName) {
            case "init" -> {
                if (args.length > INDEX_OF_FUNCTION + 1) {
                    System.out.println("Unnecessary arguments. \nUsage: glit init");
                    return null;
                }
                return new Call(functionName, null, null);
            }

            case "add" -> {
                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("File name not given. \nUsage: glit add <file1> [file2] ...");
                    return null;
                }
                for (int i = INDEX_OF_FUNCTION + 1; i < args.length; i++) {
                    if (args[i].contains("-")) {
                        System.out.println("glit add doesn't have flag " + args[i]);
                        return null;
                    }
                    Path file = Path.of(args[i]);
                    if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) || !Files.exists(file)) {
                        List<Path> files = expandWildcard(args[i]);
                        if (files == null || files.size() < 1) {
                            System.out.println("Cannot find file " + args[i]);
                            return null;
                        } else {
                            arguments.addAll(files);
                        }
                    } else {
                        arguments.add(repositoryPath.relativize(file.toAbsolutePath()));
                    }
                }
                return new Call(functionName, null, arguments);
            }
            case "commit" -> {
                String usageMessage = "Usage: glit commit -m <your_commit_name>";
                if (args.length <= INDEX_OF_FUNCTION + 2) {
                    System.out.println("Commit name not given. \n" + usageMessage);
                    return null;
                }
                if (!args[INDEX_OF_FUNCTION + 1].equals("-m")) {
                    System.out.println("Commit -m option is required. \n" + usageMessage);
                    return null;
                } else {
                    flags.add("m");
                }
                String message = args[INDEX_OF_FUNCTION + 2];
                if (args.length > INDEX_OF_FUNCTION + 3) {
                    message += " " + String.join(" ", Arrays.copyOfRange(args, INDEX_OF_FUNCTION + 3, args.length));
                }
                if (args[INDEX_OF_FUNCTION + 2].startsWith("-")) {
                    System.out.println("glit commit doesn't have flag " + args[INDEX_OF_FUNCTION + 2] + "\n" + usageMessage);
                    return null;
                }
                arguments.add(message);

                return new Call(functionName, flags, arguments);
            }
            case "checkout" -> {
                String currBranchName = Repository.getCurrentBranchName();
                List<String> allBranches = Repository.getAllBranches();
                Path branchesPath = repositoryPath.resolve(Path.of(".glit/refs/heads"));
                Path currBranchPath = repositoryPath.resolve(Path.of(".glit/refs/heads")).resolve(currBranchName);
                String usageMessage = """
                    Usage: glit checkout <branch_name>
                    For creating new branch use: glit checkout -b <new_branch_name>""";

                if (args.length <= INDEX_OF_FUNCTION + 1) {
                    System.out.println("Wrong number of arguments. \n" + usageMessage);
                    return null;
                }
                boolean creatingNewBranch = args[INDEX_OF_FUNCTION + 1].equals("-b");
                if ((creatingNewBranch && args.length != INDEX_OF_FUNCTION + 3) || (!creatingNewBranch && args.length != INDEX_OF_FUNCTION + 2)) {
                    System.out.println("Wrong number of arguments. \n" + usageMessage);
                    return null;
                }

                String branchName = creatingNewBranch ? args[INDEX_OF_FUNCTION + 2] : args[INDEX_OF_FUNCTION + 1];
                if (branchName.equals(currBranchName)) {
                    System.out.println("Branch " + branchName + " is currently being used");
                    return null;
                }
                if (!creatingNewBranch && !allBranches.contains(branchName)) {
                    System.out.println("Branch \"" + branchName + "\" not found. Available branches:"+allBranches);
                    System.out.println("--");
                    Repository.printAllBranches(branchesPath, currBranchPath);
                    return null;
                } else if (creatingNewBranch && allBranches.contains(branchName)) {
                    System.out.println("Cannot use name \"" + branchName + "\" - it has been used. Used names: ");
                    Repository.printAllBranches(branchesPath, currBranchPath);
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
                String currBranchName = Repository.getCurrentBranchName();
                List<String> allBranches = Repository.getAllBranches();
                Path branchesPath = repositoryPath.resolve(Path.of(".glit/refs/heads"));
                Path currBranchPath = repositoryPath.resolve(Path.of(".glit/refs/heads")).resolve(currBranchName);
                if (args.length != INDEX_OF_FUNCTION + 2) {
                    System.out.println("Wrong arguments. \nUsage: glit merge <branch_to_be_merged_with_your_current>");
                    return null;
                }
                String branchName = args[INDEX_OF_FUNCTION + 1];
                if (branchName.equals(currBranchName)) {
                    System.out.println("Cannot merge from branch " + branchName + " - it is currently in use.");
                    return null;
                }
                if (!allBranches.contains(branchName)) {
                    System.out.println("Branch \"" + branchName + "\" not found. Available branches:");
                    Repository.printAllBranches(branchesPath, currBranchPath);
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
                arguments.add(args[INDEX_OF_FUNCTION + 1]);
                return new Call(functionName,null,arguments);
            }
            case "status", "log" ->{
                return new Call(functionName,null,null);
            }
            case "branch" ->{
                if (args.length == 1){
                    return new Call(functionName,null,null);
                }else if(args.length == 2){
                    arguments.add(args[INDEX_OF_FUNCTION+1]);
                    return new Call(functionName,null,arguments);
                }else{
                    System.out.println("Wrong number of arguments. \nUsage: glit branch <branch-name> or glit branch");
                    return null;
                }
            }

            default -> {
                System.out.println("Glit doesn't have a function called " + functionName + ".");
                return null;
            }
        }
    }

    /**
     * <p>The main application runtime sequence.</p>
     *
     * <p>Triggers string parsing validation, handles safe failure exit boundaries,
     * and maps valid operation keys to concrete execution commands inside the
     * {@link Repository} logic block.</p>
     *
     * @param args command-line arguments provided at startup
     * @throws Exception if a low-level error occurs during backend routine executions
     */
    public static void main(String[] args) throws Exception {
        Call cliCall = validateAndParseCommandLineArgs(args);
        if (cliCall == null) {
            return;
        }

        try{
            switch (cliCall.getFunction()) {
                case "init" -> {
                    Repository.init();
                }
                case "add" -> {
                    Repository.add(cliCall);
                }
                case "commit" -> {
                    Repository.commit(cliCall);
                }
                case "checkout" -> {
                    Repository.checkout(cliCall);
                }
                case "merge" -> {
                    Repository.merge(cliCall);
                }
                case "cat-file" -> {
                    Repository.catFile(cliCall);
                }
                case "status" -> {
                    Repository.status();
                }
                case "log" -> {
                    Repository.log();
                }
                case "branch" -> {
                    Repository.branch(cliCall);
                }
            }}catch(Exception e){e.printStackTrace();throw e;}
    }
}