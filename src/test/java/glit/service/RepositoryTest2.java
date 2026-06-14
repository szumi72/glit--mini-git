package glit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.MockedConstruction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import glit.cli.Call;
import glit.model.Commit;
import glit.model.Tree;
import glit.service.Repository;
import glit.storage.ObjectReader;

class Repository2Test {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        // Przechwytujemy konsolę (System.out/err), aby móc testować wypisywane komunikaty
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ==========================================
    // 1. TESTY DLA GLIT BRANCH
    // ==========================================

    @Test
    void shouldCreateNewBranchFileWithCorrectHash(@TempDir Path tempDir) throws Exception {
        // Arrange
        Repository.REPOSITORY_PATH = tempDir;
        Path branchesPath = tempDir.resolve(".glit/refs/heads/");
        Files.createDirectories(branchesPath);

        // Symulujemy, że jesteśmy w detached HEAD z konkretnym hashem
        Path headPath = tempDir.resolve(".glit/HEAD");
        String currentCommitHash = "4a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b";
        Files.writeString(headPath, currentCommitHash);

        Call mockCall = mock(Call.class);
        when(mockCall.getArguments()).thenReturn(List.of("feature-xyz"));

        // Act
        Repository.branch(mockCall);

        // Assert
        Path newBranchPath = branchesPath.resolve("feature-xyz");
        assertTrue(Files.exists(newBranchPath), "New branch file should be created");
        assertEquals(currentCommitHash, Files.readString(newBranchPath).trim(), "Branch file should contain the active commit hash");
        assertTrue(outContent.toString().contains("Branch 'feature-xyz' created at commit 4a1b2c3"));
    }

    @Test
    void shouldNotCreateBranchIfAlreadyExists(@TempDir Path tempDir) throws Exception {
        // Arrange
        Repository.REPOSITORY_PATH = tempDir;
        Path branchesPath = tempDir.resolve(".glit/refs/heads/");
        Files.createDirectories(branchesPath);
        Files.createFile(branchesPath.resolve("main")); // plik 'main' już istnieje

        Call mockCall = mock(Call.class);
        when(mockCall.getArguments()).thenReturn(List.of("main"));

        // Act
        Repository.branch(mockCall);

        // Assert
        assertTrue(outContent.toString().contains("Branch with this name already exists"));
    }

    // ==========================================
    // 2. TESTY DLA GLIT LOG
    // ==========================================

    @Test
    void shouldPrintCommitsInLoopUntilNoParent(@TempDir Path tempDir) throws Exception {
        // 1. ARRANGE: Tworzymy strukturę gałęzi, tak jak oczekuje tego standardowy Git
        Repository.REPOSITORY_PATH = tempDir;
        Files.createDirectories(tempDir.resolve(".glit/refs/heads"));

        // HEAD wskazuje na main
        Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
        // A main wskazuje na konkretny hash commita
        Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "commitSHA1");

        // Tworzymy makiety commitów
        Commit mockCommit1 = mock(Commit.class);
        Commit mockCommit2 = mock(Commit.class);

        // Konfigurujemy relacje
        when(mockCommit1.getParentHash()).thenReturn("commitSHA2");
        when(mockCommit2.getParentHash()).thenReturn(""); // Koniec historii

        // Używamy anyString() + zwracania w łańcuchu
        try (MockedConstruction<ObjectReader> mockedReader = mockConstruction(ObjectReader.class, (mock, context) -> {
            when(mock.readObject(anyString())).thenReturn(mockCommit1, mockCommit2);
        })) {

            // 2. ACT
            Repository.log();

            // 3. ASSERT
            verify(mockCommit1, times(1)).printContent();
            verify(mockCommit2, times(1)).printContent();
        }
    }

    // ==========================================
    // 3. TESTY DLA GLIT STATUS
    // ==========================================

    @Test
    void shouldDetectUntrackedFileInStatus(@TempDir Path tempDir) throws Exception {
        // Arrange
        Repository.REPOSITORY_PATH = tempDir;
        Repository.INDEX_PATH = tempDir.resolve(".glit/index");

        // Tworzymy strukturę gałęzi dla statusu
        Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
        Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
        Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "someCommitHash");

        // Tworzymy nowy plik w katalogu roboczym (nie ma go w indeksie, więc będzie untracked)
        Path untrackedFile = tempDir.resolve("new-code.java");
        Files.writeString(untrackedFile, "public class NewCode {}");

        // Mockujemy zachowanie ObjectReader, by zwrócił puste drzewo (brak plików w HEAD)
        Commit mockCommit = mock(Commit.class);
        Tree mockTree = mock(Tree.class);
        when(mockCommit.getTreeHash()).thenReturn("treeHash");
        when(mockTree.getEntries()).thenReturn(new ArrayList<>());

        try (MockedConstruction<ObjectReader> mockedReader = mockConstruction(ObjectReader.class, (mock, context) -> {
            when(mock.readObject("someCommitHash")).thenReturn(mockCommit);
            when(mock.readObject("treeHash")).thenReturn(mockTree);
        })) {
            // Act
            Repository.status();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("On branch main"), "Should display correct branch name");
            assertTrue(output.contains("Untracked files:"), "Should contain Untracked files section");
            assertTrue(output.contains("new-code.java"), "Should list the untracked file name");
        }
    }

    @Test
    public void testInitThrowsIfAlreadyExists(@TempDir Path tempDir) throws IOException {
        // Zmień katalog
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Repository.init();
            Repository.init();
            assertTrue(outContent.toString().contains("Glit repository in ") && outContent.toString().contains(" is already initialized."));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }
}