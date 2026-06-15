package glit.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import glit.model.GlitIndex;
import glit.model.IndexEntry;

class IndexUtilsTest {

    @Test
    void writeTest() throws IOException, NoSuchAlgorithmException {
        Path tempDir = Files.createTempDirectory("glit-indexutils");
        try {
            Path indexPath = tempDir.resolve("index");
            List<IndexEntry> entries = createSampleEntries();
            GlitIndex index = new GlitIndex(2, entries);

            IndexUtils.write(index, indexPath);

            assertTrue(Files.exists(indexPath), "Index file should be created");
            assertTrue(Files.size(indexPath) > 0, "Index file should not be empty");
        } finally {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void parseTest() throws IOException, NoSuchAlgorithmException {
        Path tempDir = Files.createTempDirectory("glit-indexutils");
        try {
            Path indexPath = tempDir.resolve("index");
            List<IndexEntry> expectedEntries = createSampleEntries();
            GlitIndex expectedIndex = new GlitIndex(2, expectedEntries);

            IndexUtils.write(expectedIndex, indexPath);
            GlitIndex parsedIndex = IndexUtils.parse(indexPath);

            assertEquals(expectedIndex.getVersion(), parsedIndex.getVersion());
            assertNotNull(parsedIndex.getChecksum(), "Parsed index should contain a checksum");
            assertEquals(expectedEntries.size(), parsedIndex.getEntries().size());

            for (int i = 0; i < expectedEntries.size(); i++) {
                assertEntryEquals(expectedEntries.get(i), parsedIndex.getEntries().get(i));
            }
        } finally {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void parseInvalidIndexFileShouldThrow() throws IOException {
        Path tempDir = Files.createTempDirectory("glit-indexutils");
        try {
            Path indexPath = tempDir.resolve("bad-index");
            Files.writeString(indexPath, "NOTDIRC");

            assertThrows(IllegalStateException.class, () -> IndexUtils.parse(indexPath));
        } finally {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private static List<IndexEntry> createSampleEntries() {
        byte[] objectId = new byte[20];
        for (int i = 0; i < objectId.length; i++) {
            objectId[i] = (byte) i;
        }

        List<IndexEntry> entries = new ArrayList<>();
        entries.add(new IndexEntry(1, 2, 3, 4, 5, 6, 100644, 1000, 1000, 123, objectId, "file.txt"));
        entries.add(new IndexEntry(7, 8, 9, 10, 11, 12, 100644, 1000, 1000, 456, objectId, "src/main.c"));
        return entries;
    }

    private static void assertEntryEquals(IndexEntry expected, IndexEntry actual) {
        assertEquals(expected.getCtimeSec(), actual.getCtimeSec(), "ctimeSec should match");
        assertEquals(expected.getCtimeNsec(), actual.getCtimeNsec(), "ctimeNsec should match");
        assertEquals(expected.getMtimeSec(), actual.getMtimeSec(), "mtimeSec should match");
        assertEquals(expected.getMtimeNsec(), actual.getMtimeNsec(), "mtimeNsec should match");
        assertEquals(expected.getDev(), actual.getDev(), "dev should match");
        assertEquals(expected.getIno(), actual.getIno(), "ino should match");
        assertEquals(expected.getMode(), actual.getMode(), "mode should match");
        assertEquals(expected.getUid(), actual.getUid(), "uid should match");
        assertEquals(expected.getGid(), actual.getGid(), "gid should match");
        assertEquals(expected.getFileSize(), actual.getFileSize(), "fileSize should match");
        assertArrayEquals(expected.getObjectId(), actual.getObjectId(), "objectId should match");
        assertEquals(expected.getPath(), actual.getPath(), "path should match");
    }
}
