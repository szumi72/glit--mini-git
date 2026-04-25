package glit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;  // Poprawny import dla JUnit 5
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class RepositoryTest {

    /**
     * Test sprawdza, czy metoda init() tworzy strukturę repozytorium .glit.
     * Używa tymczasowego katalogu, aby uniknąć wpływu na rzeczywisty system
     * plików.
     *
     * @param tempDir tymczasowy katalog dostarczony przez JUnit
     * @throws IOException jeśli operacje na plikach się nie powiodą
     */
    @Test
    public void testInitCreatesRepository(@TempDir Path tempDir) throws IOException {
        // Zmień bieżący katalog na tymczasowy (symulacja)
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            // Wywołaj init
            Repository.init();

            // Sprawdź katalogi
            assertTrue(Files.exists(tempDir.resolve(".glit/objects")));
            assertTrue(Files.exists(tempDir.resolve(".glit/refs")));

            // Sprawdź pliki
            assertTrue(Files.exists(tempDir.resolve(".glit/config")));
            assertTrue(Files.exists(tempDir.resolve(".glit/HEAD")));
            assertTrue(Files.exists(tempDir.resolve(".glit/description")));
        } finally {
            // Przywróć oryginalny katalog
            System.setProperty("user.dir", originalDir);
        }
    }

    /**
     * Test sprawdza, czy init() rzuca wyjątek, jeśli katalog już istnieje (np.
     * próba ponownej inicjalizacji). Zakłada, że init() nie obsługuje
     * istniejących plików, więc może rzucić IOException.
     *
     * @param tempDir tymczasowy katalog
     * @throws IOException jeśli oczekiwany wyjątek nie zostanie rzucony
     */
    @Test
    public void testInitThrowsIfAlreadyExists(@TempDir Path tempDir) throws IOException {
        // Zmień katalog
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            // Pierwsza inicjalizacja
            Repository.init();

            // Druga próba powinna rzucić wyjątek (Files.createFile na istniejącym pliku)
            assertThrows(IOException.class, Repository::init);
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    /**
     * Test sprawdza, czy init() tworzy pliki jako puste.
     *
     * @param tempDir tymczasowy katalog
     * @throws IOException jeśli operacje się nie powiodą
     */
    @Test
    public void testInitCreatesEmptyFiles(@TempDir Path tempDir) throws IOException {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        try {
            Repository.init();

            // Sprawdź, czy pliki są puste
            assertEquals(0, Files.size(tempDir.resolve(".glit/config")));
            assertEquals(0, Files.size(tempDir.resolve(".glit/HEAD")));
            assertEquals(0, Files.size(tempDir.resolve(".glit/description")));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }
}
