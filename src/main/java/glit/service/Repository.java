package glit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

import glit.cli.Call;


/**
 * Klasa odpowiedzialna za zarządzanie repozytorium Glit. Zawiera metody do
 * inicjalizacji i lokalizacji repozytorium.
 */
public class Repository {

    /**
     * Ścieżka do katalogu głównego repozytorium.
     */
    private static Path REPOSITORY_PATH;

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

    private static boolean isIgnored(Path p) {
        return false;
    }

    private static boolean isInIndex(Path p) {
        Path indexFilePath = REPOSITORY_PATH.resolve(".glit/index");
        return false;
    }

    private static boolean isChanged(Path file) {
        if (!isInIndex(file)) {
            return true;
        }
        try {
            byte[] index = Files.readAllBytes(REPOSITORY_PATH.resolve(".glit/index"));
            try {
                BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);

                System.out.println(
                        "␣lastAccessTime:␣" + attr.lastAccessTime()
                        + "\n␣lastModifiedTime:␣" + attr.lastModifiedTime()
                        + "\n␣isDirectory:␣" + attr.isDirectory()
                        + "\n␣isRegularFile:␣" + attr.isRegularFile()
                        + "\n␣isSymbolicLink:␣" + attr.isSymbolicLink()
                        + "\n␣size:␣" + attr.size());
            } catch (IOException e) {
                throw new IOException("Couldn't read attributes of file: " + file);
            }

        } catch (IOException ex) {
            throw new Error("Couldn't read index file - aborting.");
        }
        String header = "DIRC"+"0002";
        int numberOfEntries=1;
        long ctimes=((long)1 & 0xffffffff);
        long ctimen=((long)1 & 0xffffffff);
        long mtimes=((long)1 & 0xffffffff);
        long mtimen=((long)1 & 0xffffffff);

        // ctime / mtime
        // size
        // dev + ino
        // mode - trzeba sprawdzic za pierwszym razem readable/writable/executable, wiec przy parsowaniu tego nie ma
        // uid / gid
        // policzenie hasha
        return false;
    }


    public static void add(Call cliCall) {
        REPOSITORY_PATH = whereIsRepo();
        if (REPOSITORY_PATH == null) {
            System.out.println("Glit repository not found. To start a new one type:\nglit init");
            return;
        }
        Path dir = Path.of(System.getProperty("user.dir"));
        Path diffPath = REPOSITORY_PATH.relativize(dir);
        System.out.println(diffPath + " " + REPOSITORY_PATH);
        for (Object arg : cliCall.getArguments()) {
            Path el = (Path) arg;
            if (isIgnored(el)) {
                continue;
            }
            System.out.println(el);
            if (isChanged(el)) {
                //createBlob;
                // writeHash
            }
        }
        /*
        
        // while el : args
        // check .glitignore ?
        // has el changed since last time?
        // 
        // 
        // create blob
        // write hash to index file
    // while file : args 
    // jesli w index:
        // Kolejnosc porownywania: (funkcja stat() ???)
        // ctime / mtime
        // size
        // dev + ino
        // mode - trzeba sprawdzic za pierwszym razem readable/writable/executable, wiec przy parsowaniu tego nie ma
        // uid / gid
        // policzenie hasha
        // jesli te same
            // continue
    // 
    // create blob
    // nowy wpis do index / wykreslenie starego
         */
    }

    // main only for personal tests
    public static void main(String[] args) throws Exception {
        System.out.println("Working");
        // init();
    }
}
