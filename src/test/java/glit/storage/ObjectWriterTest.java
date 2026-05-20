package glit.storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import glit.model.Blob;
import glit.model.Commit;
import glit.model.GlitObject;
import glit.model.Tree;
import glit.service.Repository;


/**
 * Tests for the ObjectWriter class to ensure proper Git-like object storage.
 */
public class ObjectWriterTest {

    /**
     * Helper method to reconstruct the file path based on Git's 2+38 hashing strategy.
     */
    private Path getObjectPath(Path tempDir, String hash){
        return tempDir.resolve(".glit/objects")
                .resolve(hash.substring(0, 2))
                .resolve(hash.substring(2));
    }


    /**
     * Verifies that all types of GlitObjects (Blob, Tree, Commit) are correctly compressed and saved.
     */
    @Test
    public void testAllObjectTypes(@TempDir Path tempDir) throws IOException {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            // Given
            Repository.init();
            Repository repo = new Repository();
            ObjectWriter writer = new ObjectWriter(tempDir);
            List<GlitObject> objects = List.of(
                    new Blob("test content".getBytes()),
                    new Tree(),
                    new Commit("message", new Blob("test".getBytes()).getHash(), "")
            );


            //When
            for (GlitObject obj : objects) {
                writer.saveObject(obj);
                Path path = getObjectPath(tempDir, obj.getHash());
                //Then
                assertTrue(Files.exists(path), "Obiekt typu " + obj.getClass().getSimpleName() + " nie został zapisany!");
            }


        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    /**
     * Ensures that if an object with the same hash already exists, it is not overwritten.
     */
    @Test
    public void testOverrideExistedObject(@TempDir Path tempDir) throws IOException, InterruptedException {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            //Given
            Repository.init();
            Repository repo = new Repository();
            ObjectWriter writer = new ObjectWriter(tempDir);

            Blob b = new Blob("test".getBytes());
            Path expectedFile = getObjectPath(tempDir,b.getHash());


            writer.saveObject(b);
            long firstWriteTime = Files.getLastModifiedTime(expectedFile).toMillis();

            //When
            Thread.sleep(50);

            writer.saveObject(b);
            long secondWriteTime = Files.getLastModifiedTime(expectedFile).toMillis();

            //Then
            assertEquals(firstWriteTime, secondWriteTime, "Czasy modyfikacji powinny być identyczne!");

        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    /**
     * Tests if the writer correctly throws an exception when the repository path is null.
     */
    @Test
    public void testSaveWithoutRepository() {

        assertThrows(IOException.class, () -> {
            ObjectWriter writer = new ObjectWriter(null);
        }, "Should throw IOException when repository path is missing");
    }
}
