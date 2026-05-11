package glit.service;

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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import glit.cli.Call;
import glit.model.Blob;
import glit.model.GlitIndex;
import glit.model.GlitObject;
import glit.model.IndexEntry;
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
        INDEX_PATH = REPOSITORY_PATH.resolve(".glit/index");
        Path dir = Path.of(System.getProperty("user.dir"));
        Path diffPath = REPOSITORY_PATH.relativize(dir); // -> przerzucic do parsowania argumentow!!!!!!!!!!!
        // System.out.println(diffPath + " " + REPOSITORY_PATH);

        boolean isAnyChanged = false;
        GlitIndex currIndex = IndexUtils.parse(INDEX_PATH);
        List<IndexEntry> entries = currIndex.getEntries();

        GlitIndex newIndex = new GlitIndex(0); // using version 0 of Glit
        ObjectWriter writer = new ObjectWriter();
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

                try (FileChannel channel = FileChannel.open(arg, StandardOpenOption.READ)) {
                    ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
                    buffer.order(ByteOrder.BIG_ENDIAN);
                    channel.read(buffer);
                    GlitObject o = new Blob(buffer.array());
                    String hash = writer.saveObject(REPOSITORY_PATH, o);
                    IndexEntry entry = new IndexEntry(arg, hash.getBytes());
                    newIndex.add(entry);
                }
                // createBlob;
                // new IndexEntry
                // GlitIndex.list.append
                isAnyChanged = true;
            }

        }
        if (!isAnyChanged) {
            System.out.println("Nothing was added - all files has been already staged.");
            return;
        }
        try {
            IndexUtils.write(newIndex, INDEX_PATH);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e);
        }

        // co z usunietymi plikami? -> chyba przy commit się stworzy nowy GlitIndex, w którym ich nie będzie
    }

    // main only for personal tests
    public static void main(String[] args) throws Exception {
        System.out.println("Working");
        // init();
    }
}
