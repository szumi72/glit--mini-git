package glit.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import glit.cli.Call;
import glit.exceptions.GlitException;
import glit.exceptions.MissingRepositoryException;
import glit.model.Blob;
import glit.model.Commit;
import glit.model.GlitIndex;
import glit.model.GlitObject;
import glit.model.IndexEntry;
import glit.model.Tree;
import glit.model.TreeEntry;
import glit.storage.ObjectReader;
import glit.storage.ObjectWriter;
import glit.util.HashUtils;
import glit.util.IndexUtils;

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
        Path dirArray[] = {REPOSITORY_PATH.resolve(".glit/objects"), REPOSITORY_PATH.resolve(".glit/refs")};
        for (Path d : dirArray) {
            try {
                Files.createDirectories(d);
                System.out.println("Created directory ./.glit/" + d.getFileName());
            } catch (IOException e) {
                System.out.println("Directory ./.glit/" + d.getFileName() + " cannot be created");
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
            System.out.println("not found in index");
            return true;
        }
        Path attrPath = REPOSITORY_PATH.resolve(file);
        BasicFileAttributes attrs = Files.readAttributes(attrPath, BasicFileAttributes.class);


        // OS independent
        if (entry.getCtimeSec() != attrs.creationTime().to(TimeUnit.SECONDS)) {
            System.out.println("csec");
            return true;
        }
        if (entry.getCtimeNsec() != ((attrs.creationTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L))) {
            System.out.println(entry.getCtimeNsec() + "   " + attrs.creationTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L);
            System.out.println("cnsec");
            return true;
        }
        if (entry.getMtimeSec() != attrs.lastModifiedTime().to(TimeUnit.SECONDS)) {
            System.out.println("msec");
            return true;
        }
        if (entry.getMtimeNsec() != attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L) {
            System.out.println("mnsec");
            return true;
        }

        // OS dependent
        if (entry.getDev() != (long) Files.getAttribute(attrPath, "unix:dev")) {
            System.out.println("dev");
            return true;
        }
        if (entry.getIno() != (long) Files.getAttribute(attrPath, "unix:ino")) {
            System.out.println("ino");
            return true;
        }
        if (entry.getMode() != (int) Files.getAttribute(attrPath, "unix:mode")) {
            System.out.println("mode");
            return true;
        }
        if (entry.getUid() != (int) Files.getAttribute(attrPath, "unix:uid")) {
            System.out.println("uid");
            return true;
        }
        if (entry.getGid() != (int) Files.getAttribute(attrPath, "unix:gid")) {
            System.out.println("gid");
            return true;
        }

        // OS independent
        if (entry.getFileSize() != attrs.size()) {
            System.out.println("size");
            return true;
        }

        // hash?
        return false;

    }

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
                    System.out.println(arg + " is changed");
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
        }catch (IndexOutOfBoundsException | MissingRepositoryException e) {
            System.err.println("cat-file err " + e);
        }
    }

    /**
     * Method that print current status of files in the project
     */
    //----Glit Status------//
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
            //jezeli commit jest pusty to wszsytko jest wypisywane jako nowe pliki
            for (String path : indexMap.keySet()) {
                System.out.println("\tnew file:\t" + path);
            }
        }else{
            Tree headTree = getHEADTree(commitHashHead);
            Map<String,String> headMap = mapHeadFiles(headTree,"");
            String output = produceStatusOutput(indexMap, headMap);
            if (output.isEmpty()) {
                System.out.println("\tnothing staged for commit");
            } else {
                System.out.print(output);
            }
        }   

        System.out.println();
        String untrackedAndUnstaged = produceUntackedFilesOutput(indexMap, wdMap);
        System.out.print(untrackedAndUnstaged);
    }

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

    //output metody status porównyje mapy plików z indexu i commita w headzie
    private static String produceStatusOutput(Map<String, String> indexMap,Map<String, String> headMap){
        StringBuilder output = new StringBuilder();

        for(String path:indexMap.keySet()){
            if(!headMap.containsKey(path)){
                    //"\n" działa na linuxach a na windowsach nie koniecznie
                output.append("\tnew file:\t").append(path).append(System.lineSeparator());

            }else if(!headMap.get(path).equals(indexMap.get(path))){
                output.append("\tmodified:\t").append(path).append(System.lineSeparator());
            }
                
        }
        for(String path:headMap.keySet()){
            if(!indexMap.containsKey(path)){
                output.append("deleted: ").append(path).append(System.lineSeparator());
            }
        }
        return output.toString();

    }

    private static String produceUntackedFilesOutput(Map<String, String> indexMap, Map<String, String> wdMap) {

        StringBuilder changesNotStaged = new StringBuilder();
        StringBuilder untrackedFiles = new StringBuilder();
            for(String path:wdMap.keySet()){
                if(indexMap.containsKey(path) && !indexMap.get(path).equals(wdMap.get(path))){                    
                    changesNotStaged.append("\t").append(path).append(System.lineSeparator());                   
                }
            }
            for(String path:wdMap.keySet()){
                if(!indexMap.containsKey(path)){
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

    private static String getLastCommitHash(Path commitPath) {
        try {
            return Files.readString(commitPath);
        } catch (IOException e) {
            return "";
        }
    }

    private static Path getPathFromHead(Path headPath) {
        if (!Files.exists(headPath)) {
            return null;
        }
        try {
            String temp = Files.readString(headPath).trim();
            String lastCommitPath;
            if (temp.startsWith("ref: ")) {
                lastCommitPath = temp.split(" ")[1];
            } else {
                lastCommitPath = temp;
            }
            return REPOSITORY_PATH.resolve(".glit").resolve(lastCommitPath);
             
        }catch (IOException e){
            System.err.println("Nie udało się odczytać pliku HEAD");
            return null;
        }
    }

    private static Tree getHEADTree(String headCommitHash) {
        try {
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            Object commit = reader.readObject(headCommitHash);
            if (commit instanceof Commit commit1) {
                String treeHash = commit1.getTreeHash();
                Object tree = reader.readObject(treeHash);
                return (Tree)tree;
            }else{
                System.err.println("HEAD has bad syntax");
                return null;
            }

        }catch (MissingRepositoryException e){
            throw e;
        }
    }

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

    private static Map<String, String> mapHeadFiles(Tree headTree, String prefix) {
        Map<String, String> headMap = new HashMap<>();
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
                    
                }catch (GlitException e){
                    throw new GlitException("cannot read object");
                }
            }
        }

        return headMap;
    }

    //---------glit status----------//

    //----glit log-------------//
    public static void log(){
        REPOSITORY_PATH = whereIsRepo();
        if (REPOSITORY_PATH == null) return;

        Path headPath = REPOSITORY_PATH.resolve(".glit/HEAD");
        try{
            if(!Files.exists(headPath) || Files.readString(headPath).isEmpty() ){
                System.out.println("No commits");
                return;
            }

            String contentHead = Files.readString(headPath).trim();
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            Commit commit;
            if(contentHead.startsWith("ref: ")){
                String commitPathStr = contentHead.replace("ref: ","").trim();
                Path commitPath = REPOSITORY_PATH.resolve(".glit").resolve(commitPathStr);
                commit = (Commit) reader.readObject(Files.readString(commitPath));
            }else{
                commit = (Commit) reader.readObject(contentHead);
            }
            int counter=0;
            while(commit!=null && counter<10){
                commit.printContent();
                String parentHash = commit.getParentHash();
                if(parentHash==null || parentHash.isEmpty()){
                    break;
                }
                commit = (Commit) reader.readObject(parentHash);
                counter++;
            }
        }catch (IOException e){
            System.out.println("log failed");
            return;
        }catch (MissingRepositoryException e){
            System.out.println(e.getMessage());
        }
    }
    //-----glit log//

     //main only for personal tests
    public static void main(String[] args) throws Exception {
        System.out.println("Working");
        // init();

    }

    /**
     * Zwraca ścieżkę do repozytorium.
     *
     * @return ścieżka do repozytorium
     */
    public Path getRepositoryPath() {
        // REPOSITORY_PATH = whereIsRepo();
        return REPOSITORY_PATH;
    }

}
