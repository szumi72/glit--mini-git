package glit.service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import glit.cli.Call;
import glit.model.*;
import glit.storage.ObjectReader;
import glit.storage.ObjectWriter;
import glit.util.IndexUtils;

/**
 * Klasa odpowiedzialna za zarządzanie repozytorium Glit. Zawiera metody do
 * inicjalizacji i lokalizacji repozytorium.
 */
public class Repository {

    /**
     * Ścieżka do katalogu głównego repozytorium.
     */
    private static Path REPOSITORY_PATH;
    private static Path INDEX_PATH;

    /**
     * Zwraca ścieżkę do repozytorium.
     *
     * @return ścieżka do repozytorium
     */
    public Path getRepositoryPath() {
        // REPOSITORY_PATH = whereIsRepo();
        return REPOSITORY_PATH;
    }

    /**
     * Znajduje ścieżkę do najbliższego repozytorium Glit, przeszukując w górę
     * drzewa katalogów.
     *
     * @return ścieżka do repozytorium lub null, jeśli nie znaleziono
     */
    static Path whereIsRepo() {
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
        Path dirArray[] = {
            REPOSITORY_PATH.resolve(".glit/objects"),
            REPOSITORY_PATH.resolve(".glit/refs")
        };
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
        Path fileArray[] = {
            REPOSITORY_PATH.resolve(".glit/config"),
            REPOSITORY_PATH.resolve(".glit/HEAD"),
            REPOSITORY_PATH.resolve(".glit/description"),
            REPOSITORY_PATH.resolve(".glit/index")
        };
        for (Path f : fileArray) {
            try {
                Files.createFile(f);
                System.out.println("Created file ./.glit/" + f.getFileName());
            } catch (IOException e) {
                System.out.println("Couldn't create " + f);
                throw e;
            }
        }

    }

    // --- ADD functionality ---
    // TODO - .glitignore file
    private static boolean isIgnored(Path p) {
        return false;
    }

    private static IndexEntry findInIndex(GlitIndex index, Path file) throws IOException {
        List<IndexEntry> entries = index.getEntries();
        Optional<IndexEntry> opt = entries.stream()
                .filter(e -> e.getPath().equals(file.toString()))
                .findFirst();
        IndexEntry entry = opt.orElse(null);
        return entry;
    }

    private static boolean isChanged(GlitIndex index, Path file) throws IOException {
        IndexEntry entry = findInIndex(index, file);
        if (entry == null) {
            return true;
        }

        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        // OS independent
        if (entry.getCtimeSec() != attrs.creationTime().to(TimeUnit.SECONDS)) {
            return true;
        }
        if (entry.getCtimeNsec() != attrs.creationTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L) {
            return true;
        }
        if (entry.getMtimeSec() != attrs.lastModifiedTime().to(TimeUnit.SECONDS)) {
            return true;
        }
        if (entry.getMtimeNsec() != attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS) % 1_000_000_000L) {
            return true;
        }

        // OS dependent
        if (entry.getDev() != (long) Files.getAttribute(file, "unix:dev")) {
            return true;
        }
        if (entry.getIno() != (long) Files.getAttribute(file, "unix:ino")) {
            return true;
        }
        if (entry.getMode() != (long) Files.getAttribute(file, "unix:mode")) {
            return true;
        }
        if (entry.getUid() != (long) Files.getAttribute(file, "unix:uid")) {
            return true;
        }
        if (entry.getGid() != (long) Files.getAttribute(file, "unix:gid")) {
            return true;
        }

        // OS independent
        if (entry.getFileSize() != attrs.size()) {
            return true;
        }

        // hash?
        return false;

    }

    public static void add(Call cliCall) throws IOException {
        REPOSITORY_PATH = whereIsRepo();

        if (REPOSITORY_PATH == null) {
            System.out.println("Glit repository not found. To start a new one type:\nglit init");
            return;
        }

        Path dir = Path.of(System.getProperty("user.dir"));
        Path diffPath = REPOSITORY_PATH.relativize(dir); // -> przerzucic do parsowania argumentow!!!!!!!!!!!
        // System.out.println(diffPath + " " + REPOSITORY_PATH);

        INDEX_PATH = REPOSITORY_PATH.resolve(".glit/index");
        boolean indexExists = Files.exists(INDEX_PATH) && Files.size(INDEX_PATH) > 0;
        GlitIndex newIndex = new GlitIndex(0); // using version 0 of Glit
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
                    entries.stream()
                            .filter(e -> e.getPath().equals(arg.toString()))
                            .forEach(entries::remove);
                    // index.arguments.pop(el) - na koniec zostana te niewywolane

                    newIndex.add(IndexEntry.createFromPath(arg, REPOSITORY_PATH));
                    isAnyChanged = true;

                }
            }
            if (!isAnyChanged) {
                System.out.println("Nothing was added - all files has been already staged.");
                return;
            }
        } else {

            Files.write(INDEX_PATH, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            for (Object el : cliCall.getArguments()) {
                Path arg = (Path) el;
                if (isIgnored(arg)) {
                    continue;
                }
                // System.out.println(el);
                newIndex.add(IndexEntry.createFromPath(arg, REPOSITORY_PATH));

            }

            try {
                IndexUtils.write(newIndex, INDEX_PATH);
            } catch (NoSuchAlgorithmException e) {
                System.out.println(e);
            }

            // co z usunietymi plikami? -> chyba przy commit się stworzy nowy GlitIndex, w którym ich nie będzie
        }
    }

    public static void catFile(Call cliCall){
        REPOSITORY_PATH = whereIsRepo();
        if(cliCall.getArguments().size()!=1){
            System.err.println("Błąd: cat-file wymaga dokładnie jednego argumentu (hash).");
            return;
        }
        try {
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            String hash = cliCall.getArguments().get(0).toString();
            GlitObject obj = reader.readObject(hash);
            if(obj!=null){
                obj.printContent();
            }
        }catch (IOException | IndexOutOfBoundsException e) {
            System.err.println("cat-file err " + e);
        }
    }

    //----Glit Status------//
    public static void status(Call cliCall){
        //przypisanie sciezki do repo
        REPOSITORY_PATH = whereIsRepo();
        if (REPOSITORY_PATH == null) return;

        INDEX_PATH = REPOSITORY_PATH.resolve(".glit/index");
        Path headPath = REPOSITORY_PATH.resolve(".glit/HEAD");

        Map<String,String> indexMap = mapIndexFiles(INDEX_PATH);

        //moze byc null jak nie było jeszcze commita wtedy wszystko do new idzie
        //String commitHashHead = getLastCommitHash(headPath);

        //////////////to dodałam nie wiem czy tak zostawiać zamiast tej linijki wyżej
        Path actualRefPath = getPathFromHead(headPath);

        String commitHashHead = "";
        if (actualRefPath != null && Files.exists(actualRefPath)) {
            commitHashHead = getLastCommitHash(actualRefPath).trim();
        }
        //////////////////////////



        //TODO wyswietlanie odpowiedniej gałęzi
        System.out.println("On branch ... ");
        System.out.println("Changes to be commited");
        if(commitHashHead.isEmpty()){
            //jezeli commit jest pusty to wszsytko jest wypisywane jako nowe pliki
            for(String path:indexMap.keySet()){
                System.out.println("new file:\t"+path);
            }
        }else{


            Tree headTree = getHEADTree(commitHashHead);
            Map<String,String> headMap = mapHeadFiles(headTree,"");
            StringBuilder output = new StringBuilder();
            for(String path:indexMap.keySet()){
                if(!headMap.containsKey(path)){
                    //"\n" działa na linuxach a na windowsach nie koniecznie
                    output.append("new file: ").append(path).append("\n");

                }else{
                    if(!headMap.get(path).equals(indexMap.get(path))){
                        output.append("modified: ").append(path).append("\n");
                    }
                }
            }
            for(String path:headMap.keySet()){
                if(!indexMap.containsKey(path)){
                    output.append("deleted: ").append(path).append("\n");
                }
            }
        }


        //TODO dokonczyc tu reszte
        //trzeba teraz porównać wartosci z index z tymi z head i odpowiednio wypisac
        //obsluzyc to że head jest pusty bo to bedzie pierwszt commit




    }


    private static String getLastCommitHash(Path commitPath){
        try {
            return Files.readString(commitPath);
        }catch (IOException e){
            throw new RuntimeException("Can't read commit from path");
        }
    }

    private static Path getPathFromHead(Path headPath){
        if (!Files.exists(headPath)) return null;
        try{
            String temp= Files.readString(headPath).trim();
            String lastCommitPath;
            if(temp.startsWith("ref: ")){
                lastCommitPath = temp.split(" ")[1];
            }else{
                lastCommitPath = temp;
            }
            Path commitPath = REPOSITORY_PATH.resolve(".glit").resolve(lastCommitPath);
            return commitPath;
        }catch (IOException e){
            throw new RuntimeException("Nie udało się odczytać pliku HEAD");
        }
    }


    private static Tree getHEADTree(String HeadCommitHash){
        try{
            ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
            Object commit = reader.readObject(HeadCommitHash);
            if(commit instanceof Commit commit1){
                String treeHash = commit1.getTreeHash();
                Object tree = reader.readObject(treeHash);
                return (Tree)tree;
            }else{
                throw new RuntimeException("HEAD has bad syntax");
            }

        }catch (IOException e){
            throw new RuntimeException("Repository path not found");
        }
    }

    private static Map<String,String> mapIndexFiles(Path indexPath){
        try{
            GlitIndex index = IndexUtils.parse(INDEX_PATH);
            List<IndexEntry> indexEntries = index.getEntries();
            Map<String,String> indexMap = new HashMap<>();
            for(IndexEntry entry: indexEntries){
                String hash = new String(entry.getObjectId());
                indexMap.put(entry.getPath(),hash);
            }
            return indexMap;

        }catch (IOException e){
            throw new RuntimeException("Index not found");
        }

    }

    private static Map<String,String > mapHeadFiles(Tree headTree,String prefix){
        Map<String,String > headMap = new HashMap<>();
        if(headTree==null){
            return headMap;
        }
        List<TreeEntry> treeEntries = headTree.getEntries();

        for(TreeEntry entry:treeEntries){
            String currentPath = prefix.isEmpty() ? entry.fileName() : prefix + "/" + entry.fileName();
            //mode bloba
            if(entry.mode().equals("100644")){
                String hash = entry.hash();
                headMap.put(currentPath,hash);
            }else{
                Map<String,String> tempMap = new HashMap<>();
                String newPath = currentPath + "/" + entry.fileName();
                try {
                    ObjectReader reader = new ObjectReader(REPOSITORY_PATH);
                    GlitObject object = reader.readObject(entry.hash());
                    if (object instanceof Tree subTree) {
                        headMap.putAll(mapHeadFiles(subTree, currentPath));
                    }
                }catch (IOException e){
                    throw new RuntimeException("cannot read object");
                }
            }

        }

        return  headMap;
    }

    //---------glit status----------//

     //main only for personal tests
    public static void main(String[] args) throws Exception {
        System.out.println("Working");
        // init();

    }


}
