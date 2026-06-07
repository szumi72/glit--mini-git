package glit.service;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import glit.cli.Call;
import glit.cli.GlitController;
import glit.exceptions.GlitException;
import glit.exceptions.MissingRepositoryException;
import glit.model.*;
import glit.storage.ObjectReader;
import glit.storage.ObjectWriter;
import glit.util.HashUtils;
import glit.util.IndexUtils;

import static java.nio.file.Files.list;

/**
 * Klasa odpowiedzialna za zarządzanie repozytorium Glit. Zawiera metody do
 * inicjalizacji i lokalizacji repozytorium.
 */
public class Repository {

    private static final List<String> ignorePatterns = new ArrayList<>();
    /**
     * Ścieżka do katalogu głównego repozytorium.
     */
    public static Path REPOSITORY_PATH;
    private static Path INDEX_PATH;

    /**
     * Znajduje ścieżkę do najbliższego repozytorium Glit, przeszukując w górę
     * drzewa katalogów.
     *
     * @return ścieżka do repozytorium lub null, jeśli nie znaleziono
     */
    public static Path whereIsRepo() {
        try {
            Path current = Paths.get(".").toRealPath();
            while (current != null) {
                if (Files.isDirectory(current.resolve(".glit"))) {
                    return current;
                }
                current = current.getParent();
            }
        } catch (IOException e) {
            System.out.println(e);
        }
        return null;
    }

    /**
     * Inicjalizuje nowe repozytorium Glit w bieżącym katalogu. Tworzy niezbędne
     * katalogi i pliki.
     *
     * @throws IOException jeśli nie można utworzyć katalogów lub plików
     */
    public static void init() throws IOException {
        REPOSITORY_PATH = whereIsRepo();
        if (whereIsRepo() != null) {
            System.out.println("Glit repository in " + REPOSITORY_PATH + " is already initialized.");
            return;
        }
        System.out.println("Creating new repository...");
        REPOSITORY_PATH = Path.of(System.getProperty("user.dir"));

        // create dirs
        Path dirArray[] = {REPOSITORY_PATH.resolve(".glit/objects"), REPOSITORY_PATH.resolve(".glit/refs/heads")};
        for (Path d : dirArray) {
            try {
                Files.createDirectories(d);
                System.out.println("Created directory " + d);
            } catch (IOException e) {
                System.out.println("Directory " + d + " cannot be created");
                throw e;
            }
        }

        // create files
        Path fileArray[] = {REPOSITORY_PATH.resolve(".glit/config"), REPOSITORY_PATH.resolve(".glit/HEAD"), REPOSITORY_PATH.resolve(".glit/description")};
        for (Path f : fileArray) {
            try {
                Files.createFile(f);
                System.out.println("Created file ./.glit/" + f.getFileName());
            } catch (IOException e) {
                System.out.println("Couldn't create " + f);
                throw e;
            }

        }
        // index with header writing
        try {
            INDEX_PATH = REPOSITORY_PATH.resolve(".glit").resolve("index");
            GlitIndex index = new GlitIndex(2);
            IndexUtils.write(index, INDEX_PATH);
            System.out.println("Created .glit/index with proper header.");

        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("Couldn't create index: " + e.getMessage());
        }
        // .glitignore with standard content
        Path glitignorePath = REPOSITORY_PATH.resolve(".glitignore");
        try (BufferedWriter writer = Files.newBufferedWriter(glitignorePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(".glit/");
            writer.newLine();
            writer.write(".glitignore");
            writer.newLine();
            System.out.println("Created .glitignore with standard content.");
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    // --- ADD functionality ---
    // currently only:
    // - directory/
    // - *.ext
    // - file
    // - empty lines and comments (# comment)
    // are valid
    private static boolean isIgnored(Path path) throws IOException {
        if (ignorePatterns.isEmpty()) {
            loadIgnorePatterns();
        }
        // System.out.println("relativizing: " + REPOSITORY_PATH + " and " + path);
        Path repoRelative = REPOSITORY_PATH.relativize(path.toAbsolutePath());
        // System.out.println(repoRelative);

        for (String pattern : ignorePatterns) {

            // directory/
            if (pattern.endsWith("/")) {
                Path dir = Path.of(pattern.substring(0, pattern.length() - 1));
                if (repoRelative.startsWith(dir)) {
                    return true;
                }
            } // *.ext
            else if (pattern.startsWith("*.")) {
                String ext = pattern.substring(1); // ".ext"
                if (repoRelative.toString().endsWith(ext)) {
                    return true;
                }
            } // exactly this file
            else {
                if (repoRelative.equals(Path.of(pattern))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void loadIgnorePatterns() throws IOException {
        Path ignoreFile = REPOSITORY_PATH.resolve(".glitignore");
        if (!Files.exists(ignoreFile)) {
            System.out.println("Could not find .glitignore file");
            return;
        }

        for (String line : Files.readAllLines(ignoreFile)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            ignorePatterns.add(trimmed);
        }
    }

    private static IndexEntry findInIndex(GlitIndex index, Path file) throws IOException {
        List<IndexEntry> entries = index.getEntries();
        Optional<IndexEntry> opt = entries.stream().filter(e -> e.getPath().equals(file.toString())).findFirst();
        IndexEntry entry = opt.orElse(null);
        return entry;
    }

    private static boolean isChanged(GlitIndex index, Path file) throws IOException {
        IndexEntry entry = findInIndex(index, file);
        if (entry == null) {
//            System.out.println("not found in index");
            return true;
        }
        Path attrPath = REPOSITORY_PATH.resolve(file);
        BasicFileAttributes attrs = Files.readAttributes(attrPath, BasicFileAttributes.class);


        // OS independent
        if (entry.getCtimeSec() != attrs.creationTime().to(TimeUnit.SECONDS)) {
//            System.out.println("csec");
            return true;
        }
        if (entry.getCtimeNsec() != ((attrs.creationTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L))) {
            System.out.println(entry.getCtimeNsec() + "   " + attrs.creationTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L);
//            System.out.println("cnsec");
            return true;
        }
        if (entry.getMtimeSec() != attrs.lastModifiedTime().to(TimeUnit.SECONDS)) {
//            System.out.println("msec");
            return true;
        }
        if (entry.getMtimeNsec() != attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L) {
//            System.out.println("mnsec");
            return true;
        }

        // OS dependent
        if (entry.getDev() != (long) Files.getAttribute(attrPath, "unix:dev")) {
//            System.out.println("dev");
            return true;
        }
        if (entry.getIno() != (long) Files.getAttribute(attrPath, "unix:ino")) {
//            System.out.println("ino");
            return true;
        }
        if (entry.getMode() != (int) Files.getAttribute(attrPath, "unix:mode")) {
//            System.out.println("mode");
            return true;
        }
        if (entry.getUid() != (int) Files.getAttribute(attrPath, "unix:uid")) {
//            System.out.println("uid");
            return true;
        }
        if (entry.getGid() != (int) Files.getAttribute(attrPath, "unix:gid")) {
//            System.out.println("gid");
            return true;
        }

        // OS independent
        if (entry.getFileSize() != attrs.size()) {
//            System.out.println("size");
            return true;
        }

        // hash?
        return false;

    }

    /**
     * Method that adds files to index.
     * If file is not staged yet, it will be added,
     * if it is staged but changed since last staging, it will be updated,
     * otherwise it will be left unchanged.
     * If file is ignored, it will be skipped.
     * If no files are added, method will return with info.
     *
     * @param cliCall - the object containing parsed command-line arguments
     * @throws IOException
     */
    public static void add(Call cliCall) throws IOException {
        REPOSITORY_PATH = whereIsRepo();

        if (REPOSITORY_PATH == null) {
            System.out.println("Glit repository not found. To start a new one type:" + System.lineSeparator() + "glit init");
            return;
        }

        Path dir = Path.of(System.getProperty("user.dir"));

        INDEX_PATH = REPOSITORY_PATH.resolve(".glit/index");
        boolean indexExists = Files.exists(INDEX_PATH) && Files.size(INDEX_PATH) > 0;
        GlitIndex newIndex = new GlitIndex(2); // using version 2 of Glit - to be compatible with git
        ObjectWriter writer = new ObjectWriter(REPOSITORY_PATH);
        if (indexExists) {
            boolean isAnyChanged = false;
            GlitIndex currIndex = IndexUtils.parse(INDEX_PATH);
            List<IndexEntry> entries = currIndex.getEntries();

            for (Object el : cliCall.getArguments()) {
                Path arg = (Path) el;
                if (isIgnored(arg)) {
                    continue;
                }
                // System.out.println(el);
                if (isChanged(currIndex, arg)) {
//                    System.out.println(arg + " is changed");
                    entries.removeIf(e -> e.getPath().equals(arg.toString()));
                    newIndex.add(IndexEntry.createFromPath(arg, REPOSITORY_PATH));
                    isAnyChanged = true;

                }
            }
            if (!isAnyChanged) {
                System.out.println("Nothing was added - all files has been already staged.");
                return;
            } else {
                newIndex.addAll(entries);
            }
        } else
//        index somehow not existing
        {

            // Files.write(INDEX_PATH, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); -> in IndexUtils there is doubling

            for (Object el : cliCall.getArguments()) {
                Path arg = (Path) el;
                if (isIgnored(arg)) {
                    continue;
                }
                // System.out.println(el);
                newIndex.add(IndexEntry.createFromPath(arg, REPOSITORY_PATH));

            }

            // co z usunietymi plikami? -> chyba przy commit się stworzy nowy GlitIndex, w którym ich nie będzie
        }
        // writing newIndex - if nothing is changed the function will return with info (look up)
        try {
            IndexUtils.write(newIndex, INDEX_PATH);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e);
        }

    }

    /**
     * Prints the content of an object (e.g., a Blob) stored in the Glit database based on the provided hash.
     * The command requires exactly one argument representing the object's hash.
     *
     * @param cliCall the object containing parsed command-line arguments
     */
    public static void catFile(Call cliCall) {
        REPOSITORY_PATH = whereIsRepo();
        if (cliCall.getArguments().size() != 1) {
            System.err.println("Error: cat-file require one argument.");
            return;
        }
        try {
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            String hash = cliCall.getArguments().get(0).toString();
            GlitObject obj = reader.readObject(hash);
            if (obj != null) {
                obj.printContent();
            }
        } catch (IndexOutOfBoundsException | MissingRepositoryException e) {
            System.err.println("cat-file err " + e);
        }
    }

    /**
     * Method that creates new commit with files staged in index.
     * If there is no commit in head, all staged files will be commited as new files,
     * otherwise they will be compared to files in head and marked as modified, new or deleted.
     * After commit, index should be cleared.
     *
     * @param cliCall -  the object containing parsed command-line arguments
     */
    public static void commit(Call cliCall) throws IOException {
        REPOSITORY_PATH=whereIsRepo();
        INDEX_PATH = REPOSITORY_PATH.resolve(".glit/index");
        if(Files.size(INDEX_PATH)==0){
            System.out.println("Nothing to commit. Add something first (glit add <file1> [file2]...)");
            return;
        }
        List<Path> stagedFiles = IndexUtils.parse(INDEX_PATH).getEntries().stream()
                .map(e -> Path.of(e.getPath()))
                .toList();
        if (stagedFiles.isEmpty()) {
            System.out.println("Index is empty - nothing to commit. Add your changes first (glit add <file1> [file2] ...)");
            return;
        }

        String message = cliCall.getArguments().get(0).toString();

//        parent identifying
        Path headPath = REPOSITORY_PATH.resolve(".glit/HEAD");
        Path branchRef = getPathFromHead(headPath);
        // when there's no branch or working detached
        boolean isInObjects = branchRef==null ? false : branchRef.getParent().getParent().equals(REPOSITORY_PATH.resolve(".glit").resolve("objects"));
        String idParent = branchRef==null ? "" : isInObjects ? REPOSITORY_PATH.resolve(".glit").resolve("objects").relativize(branchRef).toString().replace("/","") : getLastCommitHash(branchRef);
//        creating tree
        Tree commitTree = Tree.createAndWriteTree(mapIndexFiles(INDEX_PATH));

        Commit commit = new Commit(message, commitTree.getHash(), idParent);
        ObjectWriter writer = new ObjectWriter(REPOSITORY_PATH);
        writer.saveObject(commit);


        if(branchRef==null || isInObjects){
            try(BufferedWriter w = Files.newBufferedWriter(headPath, StandardOpenOption.TRUNCATE_EXISTING)){
                w.write(commit.getHash());
                w.newLine();
            }catch(Exception e){e.printStackTrace();}
        }else{
            // when working on branch
            setLastCommitHash(branchRef, commit.getHash());
        }

        String branchName=getCurrentBranchName();
        System.out.println("On branch " + branchName);
        System.out.println(" [" + branchName + " " + commit.getHash().substring(0,7) + "] " + message);

//        index cleaning
        try(BufferedWriter w = Files.newBufferedWriter(INDEX_PATH , StandardOpenOption.TRUNCATE_EXISTING)){}catch(Exception e){e.printStackTrace();}

    }

    /**
     * @return name of the branch currently in use
     */
    public static String getCurrentBranchName(){
        REPOSITORY_PATH = whereIsRepo();
        if(REPOSITORY_PATH == null)
            return null;
        Path headPath = REPOSITORY_PATH.resolve(".glit").resolve("HEAD");
        String branchName="";
        try {
            String headContent = Files.readString(headPath).trim();
            branchName = headContent.startsWith("ref: refs/heads/") ? headContent.replace("ref: refs/heads/", "") : "detached HEAD";
        } catch (IOException e) {e.printStackTrace();}
        return branchName;
    }

    public static String getAnyBranchName(Path branchPath){
        return branchPath.toString().replaceFirst(".*refs/heads/", "");
    }

    /**
     * Checks and displays the current state of the repository (equivalent to 'git status').
     * <p>
     * This method informs the user about:
     * <ul>
     * <li>The current active branch (or detached HEAD state)</li>
     * <li>Changes staged for commit (Changes to be committed)</li>
     * <li>Changes tracked but modified locally (Changes not staged for commit)</li>
     * <li>Untracked files in the working directory</li>
     * </ul>
     * </p>
     */
     public static void status() {

        //przypisanie sciezki do repo
        REPOSITORY_PATH = whereIsRepo();
        if (REPOSITORY_PATH == null || !Files.exists(REPOSITORY_PATH.resolve(".glit"))) return;

        INDEX_PATH = REPOSITORY_PATH.resolve(".glit/index");
        Path headPath = REPOSITORY_PATH.resolve(".glit/HEAD");

        Map<String, String> indexMap = mapIndexFiles(INDEX_PATH);
        Map<String, String> wdMap = mapWorkingDirectory();

        Path actualRefPath = getPathFromHead(headPath);

        String commitHashHead = "";
        if (actualRefPath != null && Files.exists(actualRefPath)) {
            commitHashHead = getLastCommitHash(actualRefPath);
        }

        try {
            String headContent = Files.readString(headPath).trim();
            String branchName = headContent.startsWith("ref: refs/heads/") ? headContent.replace("ref: refs/heads/", "") : "detached HEAD";
            System.out.println("On branch " + branchName);
        } catch (IOException e) {
            System.out.println("On branch unknown");
        }

        System.out.println("Changes to be committed");

        if (commitHashHead.isEmpty()) {
            //jezeli commit jest pusty to wszystko jest wypisywane jako nowe pliki
            for (String path : indexMap.keySet()) {
                System.out.println("\tnew file:\t" + path);
            }
        } else {
            Tree headTree = getHEADTree(commitHashHead);
            Map<String, String> headMap = mapHeadFiles(headTree, "");
            String output = produceStatusOutput(indexMap, headMap);
            if (output.isEmpty()) {
                System.out.println("\tnothing staged for commit");
            } else {
                System.out.print(output);
            }
        }   

        System.out.println();
        String untrackedAndUnstaged = produceUntrackedFilesOutput(indexMap, wdMap);
        System.out.print(untrackedAndUnstaged);
    }

    /**
     * Scans the working directory and maps regular files to their current content hashes.
     * It ignores internal metadata directories (.glit, .git) and build directories (target).
     *
     * @return a map containing relative file paths as keys and their corresponding content hashes as values
     */
    private static Map<String, String> mapWorkingDirectory() {
        Map<String, String> dirMap = new HashMap<>();
        try (Stream<Path> paths = Files.walk(REPOSITORY_PATH)) {
            paths.filter(Files::isRegularFile).filter(path -> (!path.startsWith(REPOSITORY_PATH.resolve(".glit")))).filter(path -> !path.startsWith(REPOSITORY_PATH.resolve(".git"))).filter(path -> !path.startsWith(REPOSITORY_PATH.resolve("target"))).forEach(path -> {
                try {
                    byte[] content = Files.readAllBytes(path);
                    Blob temp = new Blob(content);
                    String relativePath = REPOSITORY_PATH.relativize(path).toString();
                    dirMap.put(relativePath, temp.getHash());
                } catch (IOException e) {
                    System.err.println("Couldn't read file" + path);
                }

            });

        } catch (IOException e) {
            System.err.println("Cannot read working directory");
        }
        return dirMap;
    }

    /**
     * Compares the index (staging area) with the latest commit (HEAD).
     * Generates a formatted string detailing new, modified, or deleted files.
     *
     * @param indexMap a map of files currently in the index [path -> hash]
     * @param headMap  a map of files in the latest HEAD commit [path -> hash]
     * @return a formatted string representing the "Changes to be committed" section
     */
    private static String produceStatusOutput(Map<String, String> indexMap,Map<String, String> headMap){
        StringBuilder output = new StringBuilder();

        for (String path : indexMap.keySet()) {
            if (!headMap.containsKey(path)) {
                //"\n" działa na linuxach a na windowsach nie koniecznie
                output.append("\tnew file:\t").append(path).append(System.lineSeparator());

            } else if (!headMap.get(path).equals(indexMap.get(path))) {
                output.append("\tmodified:\t").append(path).append(System.lineSeparator());
            }

        }
        for (String path : headMap.keySet()) {
            if (!indexMap.containsKey(path)) {
                output.append("deleted: ").append(path).append(System.lineSeparator());
            }
        }
        return output.toString();

    }

    /**
     * Compares the working directory with the index to detect untracked files
     * and local modifications that have not yet been staged.
     *
     * @param indexMap a map of files currently in the index [path -> hash]
     * @param wdMap    a map of files existing in the working directory [path -> hash]
     * @return a formatted string describing unstaged modifications and untracked files
     */
    private static String produceUntrackedFilesOutput(Map<String, String> indexMap, Map<String, String> wdMap) {

        StringBuilder changesNotStaged = new StringBuilder();
        StringBuilder untrackedFiles = new StringBuilder();
        for (String path : wdMap.keySet()) {
            if (indexMap.containsKey(path) && !indexMap.get(path).equals(wdMap.get(path))) {
                changesNotStaged.append("\t").append(path).append(System.lineSeparator());
            }
        }
        for (String path : wdMap.keySet()) {
            if (!indexMap.containsKey(path)) {
                untrackedFiles.append("\t").append(path).append(System.lineSeparator());
            }
        }
        StringBuilder finalOutput = new StringBuilder();
        if (!changesNotStaged.isEmpty()) {
            finalOutput.append("Changes not staged for commit:").append(System.lineSeparator()).append(changesNotStaged).append(System.lineSeparator());
        }
        if (!untrackedFiles.isEmpty()) {
            finalOutput.append("Untracked files:").append(System.lineSeparator()).append(untrackedFiles).append(System.lineSeparator());
        }
        return finalOutput.toString();
    }

    /**
     * Retrieves the commit hash stored inside a specific branch reference file.
     *
     * @param commitPath the path to the branch file (e.g., .glit/refs/heads/main)
     * @return the commit hash as a String, or an empty string if reading fails
     */
    private static String getLastCommitHash(Path commitPath) {
        try {
            return Files.readString(commitPath);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Stores the commit hash inside a specific branch reference file.
     * @param commitPath - branch reference file
     * @param commitHash - hash of a new commit
     */
    private static void setLastCommitHash(Path commitPath, String commitHash) {
        try(BufferedWriter w = Files.newBufferedWriter(commitPath, StandardOpenOption.TRUNCATE_EXISTING)){
            w.write(commitHash);
        }catch(Exception e){e.printStackTrace();}
    }


    /**
     * Parses the HEAD file and resolves the full system path to the current branch file
     * that HEAD points to.
     *
     * @param headPath the path to the .glit/HEAD file
     * @return the path to the active branch reference file, or a path pointing directly to an object hash in detached HEAD mode
     */
    public static Path getPathFromHead(Path headPath) {
        if (!Files.exists(headPath)) {
            return null;
        }
        try {
            String temp = Files.readString(headPath).trim();
            String lastCommitPath;
            if (temp.startsWith("ref: ")) {
                lastCommitPath = temp.split(" ")[1];
                return REPOSITORY_PATH.resolve(".glit").resolve(lastCommitPath);
            } else if (!temp.isEmpty()) {
//                was:
//                lastCommitPath = temp;
//                because not able to read in detached mode, changed to:
                return REPOSITORY_PATH.resolve(".glit").resolve("objects").resolve(temp.substring(0,2)).resolve(temp.substring(2));
            }else{
                return null;
            }

        } catch (IOException e) {
            System.err.println("Nie udało się odczytać pliku HEAD");
            return null;
        }
    }
    /**
     * Retrieves the root Tree object associated with the given head commit hash.
     *
     * @param headCommitHash the hash of the commit from which to extract the root tree
     * @return a Tree object representing the project file structure at that commit snapshot
     * @throws MissingRepositoryException if the repository is not initialized properly
     */
    private static Tree getHEADTree(String headCommitHash) {
        try {
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            Object commit = reader.readObject(headCommitHash);
            if (commit instanceof Commit commit1) {
                String treeHash = commit1.getTreeHash();
                Object tree = reader.readObject(treeHash);
                return (Tree) tree;
            } else {
                System.err.println("HEAD has bad syntax");
                return null;
            }

        } catch (MissingRepositoryException e) {
            throw e;
        }
    }


    /**
     * Parses the binary index file (.glit/index) and converts it into a flat file map.
     *
     * @param indexPath the path to the index file
     * @return a map containing file paths and their corresponding hex string hashes
     */
    private static Map<String,String> mapIndexFiles(Path indexPath){
        Map<String,String> indexMap = new HashMap<>();
        try{
            if(!Files.exists(indexPath) || Files.size(indexPath)==0){                
                return indexMap;
            }
            GlitIndex index = IndexUtils.parse(indexPath);
            List<IndexEntry> indexEntries = index.getEntries();

            for (IndexEntry entry : indexEntries) {
                String hash = HashUtils.byteArrayToHexString(entry.getObjectId());
                indexMap.put(entry.getPath(), hash);
            }

        } catch (IOException e) {
            System.err.println("Index not found " + e.getMessage());
        }
        return indexMap;
    }


    /**
     * Recursively traverses a Git Tree object and builds a flat map of all nested
     * files (Blobs) with their full relative paths.
     *
     * @param headTree the current tree object being analyzed
     * @param prefix   the accumulated parent directory path prefix
     * @return a map of all nested files discovered in the tree structure [full_path -> hash]
     * @throws GlitException if an error occurs while reading sub-objects from disk
     */
    private static Map<String, String> mapHeadFiles(Tree headTree, String prefix) {
//        changed HashMap to TreeMap to get sorted records
        Map<String, String> headMap = new TreeMap<>();
        if (headTree == null) {
            return headMap;
        }
        List<TreeEntry> treeEntries = headTree.getEntries();

        for (TreeEntry entry : treeEntries) {
            String currentPath = prefix.isEmpty() ? entry.fileName() : prefix + "/" + entry.fileName();
            //mode bloba
            if (entry.mode().equals("100644")) {
                String hash = entry.hash();
                headMap.put(currentPath, hash);
                //jak nie jest blobem to jest tree
            } else {

                String newPath = currentPath;
                try {
                    ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
                    GlitObject object = reader.readObject(entry.hash());
                    if (object instanceof Tree subTree) {
                        headMap.putAll(mapHeadFiles(subTree, newPath));
                    }

                } catch (GlitException e) {
                    throw new GlitException("cannot read object");
                }
            }
        }

        return headMap;
    }

    //---------glit status----------//

    //----glit log-------------//

    /**
     * Traverses the commit history backwards, starting from the current HEAD state.
     * Prints the content metadata of up to 10 recent commits by following parent hashes.
     */
    public static void log(){
        REPOSITORY_PATH = whereIsRepo();
        if (REPOSITORY_PATH == null) return;

        Path headPath = REPOSITORY_PATH.resolve(".glit/HEAD");
        try {
            if (!Files.exists(headPath) || Files.readString(headPath).isEmpty()) {
                System.out.println("No commits");
                return;
            }

            String contentHead = Files.readString(headPath).trim();
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            Commit commit;
            if (contentHead.startsWith("ref: ")) {
                String commitPathStr = contentHead.replace("ref: ", "").trim();
                Path commitPath = REPOSITORY_PATH.resolve(".glit").resolve(commitPathStr);
                commit = (Commit) reader.readObject(Files.readString(commitPath));
            } else {
                commit = (Commit) reader.readObject(contentHead);
            }
            int counter = 0;
            while (commit != null && counter < 10) {
                commit.printContent();
                String parentHash = commit.getParentHash();
                if (parentHash == null || parentHash.isEmpty()) {
                    break;
                }
                commit = (Commit) reader.readObject(parentHash);
                counter++;
            }
        } catch (IOException e) {
            System.out.println("log failed");
            return;
        } catch (MissingRepositoryException e) {
            System.out.println(e.getMessage());
        }
    }
    //-----glit log//

    //main only for personal tests
    public static void main(String[] args) throws Exception {
        System.out.println("Working");
        // init();
        String[] ar = {"glit","branch"};
        Call cliCall = GlitController.validateAndParseCommandLineArgs(ar);
        Repository.branch(cliCall);

    }

    //-------------glit branch---------------//

    /**
     * Handles branch management operations (equivalent to 'git branch').
     * <p>
     * Operates in two distinct modes depending on provided arguments:
     * <ol>
     * <li><b>No arguments:</b> Scans and lists all existing branch reference files in refs/heads,
     * highlighting the currently active branch with a leading "*" symbol.</li>
     * <li><b>One argument:</b> Creates a new branch reference file pointing to the current commit,
     * ensuring first that a branch with the requested name does not already exist.</li>
     * </ol>
     * </p>
     *
     * @param cliCall the object containing parsed command-line arguments
     */
    public static void branch(Call cliCall){

        REPOSITORY_PATH = whereIsRepo();
        if (REPOSITORY_PATH == null) return;
        Path brachesPath = REPOSITORY_PATH.resolve(".glit/refs/heads/");
        Path headPath = REPOSITORY_PATH.resolve(".glit/HEAD");
        Path currentBranchPath = getPathFromHead(headPath);

        //wyslietlanie branchy
        if(cliCall.getArguments() == null || cliCall.getArguments().isEmpty()){
            printAllBranches(brachesPath, currentBranchPath);
        }else if(cliCall.getArguments().size() == 1){

            String newBranchName = cliCall.getArguments().get(0).toString();

            if(Files.exists(brachesPath.resolve(newBranchName))){
                System.out.println("Branch with this name already exists");
                return;
            }
            Path newBranchPath = brachesPath.resolve(newBranchName);
            try {
                String currentCommitHash = "";
                String headContent = Files.readString(headPath).trim();

                if (headContent.startsWith("ref: ")) {

                    if (currentBranchPath != null && Files.exists(currentBranchPath)) {
                        currentCommitHash = Files.readString(currentBranchPath).trim();
                    }
                } else {
                    currentCommitHash = headContent;
                }

                if (currentCommitHash.isEmpty()) {
                    System.out.println("fatal: Cannot create branch because there are no commits yet.");
                    return;
                }

                if(Files.exists(newBranchPath))
                    Files.createFile(newBranchPath);
                Files.writeString(newBranchPath, currentCommitHash);
                System.out.println("Branch '" + newBranchName + "' created at commit " + currentCommitHash.substring(0, 7) + "...");

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("fatal: glit branch error while creating file");
            }

        }
    }

    /**
     * prints all branches marking the branch in use
     * @param branchesPath - Path to .glit/refs/heads
     * @param currentBranchPath - Path to the branch in use
     */
    public static void printAllBranches(Path branchesPath, Path currentBranchPath) {
        try (
            Stream<Path> paths = list(branchesPath)){
            paths.forEach(path ->{String branchName = path.getFileName().toString();
                if(currentBranchPath != null && Files.exists(currentBranchPath) && branchName.equals( currentBranchPath.getFileName().toString())){

                    System.out.println("*   " + branchName);
                }else{
                    System.out.println("\t" + branchName);
                }
            });

        }catch (IOException e){
            e.printStackTrace();
            System.err.println("Error while reading from files");
        }
    }

    /**
     *
     * @return list of all branches names
     */
    public static List<String> getAllBranches(){
        Path branchesPath = REPOSITORY_PATH.resolve(".glit").resolve("refs").resolve("heads");
        List<String> branches;
        try (Stream<Path> s = Files.list(branchesPath)){
            branches = s.map(Repository::getAnyBranchName).toList();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Couldn't read branches.");
            return null;
        }
        return branches;
    }

    /**
     * checks whether branch of a given name exists (looks in the .glit/refs/heads/ directory)
     * @param branchName - branch name
     * @return true if branch named branchName exists
     */
    private static boolean branchExists(String branchName){
        Path branchesPath = REPOSITORY_PATH.resolve(".glit/refs/heads/");
        return Files.exists(branchesPath.resolve(branchName));
    }

    /**
     * with -b parameter creates new branch and checkouts to it
     * otherwise checkouts to already existing branch and restores file structure
     * @param cliCall
     * @throws IOException
     */
    public static void checkout(Call cliCall) throws IOException {
        REPOSITORY_PATH = whereIsRepo();
        INDEX_PATH = REPOSITORY_PATH.resolve(".glit/index");
        boolean creatingNewBranch = cliCall.getFlags().contains("b");
        if(creatingNewBranch){
            branch(cliCall);
        }
//        System.out.println("DEBUG1");
        String branchName = (String) cliCall.getArguments().getFirst();


        // check if there are staged files
        try {
            if(Files.size(INDEX_PATH)!=0){
                System.out.println("There are staged files. Commit them first, then checkout.");
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
//        System.out.println("DEBUG2");
        Path newBranchPath = REPOSITORY_PATH.resolve(".glit/refs/heads").resolve(branchName);
        Path currBranchPath = getPathFromHead(REPOSITORY_PATH.resolve(".glit").resolve("HEAD"));
        System.out.println("currBranchPath = "+currBranchPath);

        // restore file structure from branch's last commit
        if(!creatingNewBranch && !restoreFileStructureFromBranchLastCommit(newBranchPath, currBranchPath)){
            System.out.println("Fatal: couldn't restore file structure.");
            return;
        }

        // change head
        try {
            Files.writeString(REPOSITORY_PATH.resolve(".glit").resolve("HEAD"), "ref: " + newBranchPath.toString().replaceFirst(".*\\.glit/", ""));
        } catch (IOException e) {
            System.err.println("Fatal: HEAD couldn't be overwritten.");
            throw e;
        }
//        System.out.println("DEBUG3");
        System.out.println("Switched to a "+ (creatingNewBranch ? "new " : "") +"branch '"+branchName+"'");

    }

    private static boolean restoreFileStructureFromBranchLastCommit(Path newBranchPath, Path currBranchPath){
        try {
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            String commitHash = getLastCommitHash(newBranchPath);
            Commit commit = (Commit) reader.readObject(commitHash);
            String newTreeHash = commit.getTreeHash();

            String currCommitHash = getLastCommitHash(currBranchPath);
            Commit currCommit = (Commit) reader.readObject(currCommitHash);
            String currTreeHash = currCommit.getTreeHash();
            try {
                deleteFilesFromTreeHash(REPOSITORY_PATH, currTreeHash);
                buildDirFromTreeHash(REPOSITORY_PATH, newTreeHash);
            }catch (IOException e){
                System.err.println("Fatal: couldn't restore file structure from branch.");
                e.printStackTrace();
                return false;
            }

        } catch (IndexOutOfBoundsException | MissingRepositoryException e) {
            e.printStackTrace();
        }
        return true;
    }

    private static void buildDirFromTreeHash(Path dirPath, String treeHash) throws IOException{
        ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
        Tree tree = (Tree) reader.readObject(treeHash);
        List<TreeEntry> entries = tree.getEntries();
        for(TreeEntry entry : entries){
            switch (entry.mode()){
                case "100644" -> createFileFromBlobHash(dirPath.resolve(entry.fileName()), entry.hash());
                case "040000" -> {
                    Path newDirPath = dirPath.resolve(entry.fileName());
                    // check if a new directory must be created
                    if(!Files.isDirectory(newDirPath)){
                        Files.createDirectory(newDirPath);
                    }
                    buildDirFromTreeHash(newDirPath, entry.hash());
                }
                default -> throw new IOException("Object mode not known: " + entry.mode());
            }
        }
    }

    private static void deleteFilesFromTreeHash(Path dirPath, String treeHash) throws IOException{
        ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
        Tree tree = (Tree) reader.readObject(treeHash);
        List<TreeEntry> entries = tree.getEntries();
        for(TreeEntry entry : entries){
            switch (entry.mode()){
                case "100644" -> Files.delete(dirPath.resolve(entry.fileName()));
                case "040000" -> {
                    Path newDirPath = dirPath.resolve(entry.fileName());
                    // check if a new directory must be created
                    if(Files.isDirectory(newDirPath)){
                        deleteFilesFromTreeHash(newDirPath, entry.hash());
                    }
                }
                default -> throw new IOException("Object mode not known: " + entry.mode());
            }
        }
    }

    private static void createFileFromBlobHash(Path fileName, String blobHash)throws IOException{
//        unpack Blob content
        ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
        Blob blob = (Blob) reader.readObject(blobHash);
        try {
            Files.write(fileName, blob.getContent());
        } catch (IOException e) {
            throw new IOException("Fatal: couldn't restore file: "+fileName);
        }
    }

    /**
     * Path of an object getter
     * @param hash - hash in String of a GlitObject
     * @return Path to GlitObject file (.glit/objects/??/****)
     */
    public static Path getObjectPathFromHash(String hash){
        return REPOSITORY_PATH.resolve(".glit").resolve("objects").resolve(hash.substring(0,2)).resolve(hash.substring(2));
    }



}
