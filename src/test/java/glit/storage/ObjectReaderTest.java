package glit.storage;

import glit.model.Blob;
import glit.model.GlitObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ObjectReaderTest{
    @TempDir
    Path tempDir;

    /**
     * Checks if object is properly saved and read
     * @throws IOException if repository Path not founds
     */
    @Test
    public void writeAndReadObject() throws IOException {
        // Given
        String mes = "Hello Glit";
        byte[] content = mes.getBytes(StandardCharsets.UTF_8);
        Blob b = new Blob(content);

        Path objectsPath = tempDir.resolve(".glit/objects");
        Files.createDirectories(objectsPath);

        ObjectWriter writer = new ObjectWriter(tempDir);
        ObjectReader reader = new ObjectReader(tempDir);

        // When
        String hash = writer.saveObject(b);
        GlitObject resultBlob = reader.readObject(hash);

        // Then
        assertTrue(resultBlob instanceof Blob, "Odczytany obiekt powinien być Blobem");
        Blob blob = (Blob) resultBlob;

        String actualContent = new String(blob.getContent(), StandardCharsets.UTF_8);
        assertEquals(mes, actualContent, "Treść bloba powinna być identyczna");
    }
}