package glit.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Repository {

    private static Path REPOSITORY_PATH;

    public Path getRepositoryPath() {
        return REPOSITORY_PATH;
    }

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

    static void init() throws IOException {
        REPOSITORY_PATH = whereIsRepo();
        if (whereIsRepo() != null) {
            System.out.println("Glit repository in " + REPOSITORY_PATH + " is already initialized.");
            return;
        }
        System.out.println("Creating new repository...");
        REPOSITORY_PATH = Path.of(System.getProperty("user.dir"));

        // create dirs
        File dirArray[] = {
            new File(REPOSITORY_PATH + "/.glit/objects"),
            new File(REPOSITORY_PATH + "/.glit/refs")
        };
        for (File d : dirArray) {
            if (d.mkdirs()) {
                System.out.println("Directory ./.glit/" + d.getName() + " is created");
            } else {
                System.out.println("Directory ./.glit/" + d.getName() + " cannot be created");
            }
        }

        // create files
        File filesArray[] = {
            new File("./.glit/config"),
            new File("./.glit/HEAD"),
            new File("./.glit/description")
        };
        for (File f : filesArray) {
            try {
                f.createNewFile();
            } catch (IOException ioe) {
                System.out.println("File ./.glit/" + f.getName() + " cannot be created");
                throw ioe;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        init();
    }
}
