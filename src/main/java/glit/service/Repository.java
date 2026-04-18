package glit.service;

import java.io.File;
import java.io.IOException;

public class Repository {

    public static final String REPOSITORY_PATH = System.getProperty("user.dir");

    static void init() throws IOException {
        System.out.println(REPOSITORY_PATH);
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
