package glit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import glit.model.GlitIndex;
import glit.model.IndexEntry;
import glit.util.IndexUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
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

    // ==========================================
    // 4. TESTY DLA GLIT INIT
    // ==========================================
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

    @Test
    public void testInitSerrIfAlreadyExists(@TempDir Path tempDir) throws IOException {
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


    // ==========================================
    // 5. TESTY DLA GLIT ADD
    // ==========================================

    @Test
    void testAddAddsNewFileToEmptyIndex(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit"));
            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
            // given
            Path file = tempDir.resolve("file.txt");
            Files.writeString(file, "hello");

            Call call = new Call("add", List.of(), List.of(file));

            // when
            Repository.add(call);

            // then
            assertTrue(Files.exists(Repository.INDEX_PATH));
            assertTrue(Files.size(Repository.INDEX_PATH) > 0);

            GlitIndex index = IndexUtils.parse(Repository.INDEX_PATH);
            assertEquals(1, index.getEntries().size());
            assertEquals("file.txt", Path.of(index.getEntries().get(0).getPath()).getFileName().toString());
        }finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testAddIgnoresFilesFromGlitignore(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit"));
            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
            // given
            Files.writeString(tempDir.resolve(".glitignore"), "*.log");

            Path ignored = tempDir.resolve("debug.log");
            Files.writeString(ignored, "test");

            Call call = new Call("add", List.of(), List.of(ignored));

            // when
            Repository.add(call);

            // then
            GlitIndex index = IndexUtils.parse(Repository.INDEX_PATH);
            assertTrue(index.getEntries().isEmpty());
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testAddUpdatesChangedFile(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit"));
            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
            // given
            Path file = tempDir.resolve("file.txt");
            Files.writeString(file, "hello");

            Call call = new Call("add", null, List.of(file));
            Repository.add(call);

            GlitIndex firstIndex = IndexUtils.parse(Repository.INDEX_PATH);
            IndexEntry firstEntry = firstIndex.getEntries().get(0);

            // modify file
            Thread.sleep(1); // ensure timestamp changes
            Files.writeString(file, "changed");

            // when
            Repository.add(call);

            // then
            GlitIndex secondIndex = IndexUtils.parse(Repository.INDEX_PATH);
            IndexEntry secondEntry = secondIndex.getEntries().get(0);

            assertNotEquals(firstEntry.getMtimeNsec(), secondEntry.getMtimeNsec());
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testAddNoChangesPrintsMessage(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit"));
            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
            // given
            Path file = tempDir.resolve("file.txt");
            Files.writeString(file, "hello");

            Call call = new Call("add", null, List.of(Path.of("file.txt")));
            Repository.add(call);

            // when
            Repository.add(call);

            // then
            assertTrue(outContent.toString().contains("Nothing was added"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testAddMultipleFiles(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit"));
            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");
            Path a = tempDir.resolve("a.txt");
            Path b = tempDir.resolve("b.txt");
            Files.writeString(a, "A");
            Files.writeString(b, "B");

            Call call = new Call("add", List.of(), List.of(a, b));

            Repository.add(call);

            GlitIndex index = IndexUtils.parse(Repository.INDEX_PATH);

            assertEquals(2, index.getEntries().size());
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    // ==========================================
    // 5. TESTY DLA GLIT COMMIT
    // ==========================================

    @Test
    void testCommitShouldPrintMessageWhenCommitIndexIsEmpty(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit"));
            Files.createFile(tempDir.resolve(".glit/index"));
            Repository.REPOSITORY_PATH = tempDir;

            Call call = new Call("commit", List.of(), List.of("initial commit"));

            Repository.commit(call);

            assertTrue(outContent.toString().contains("Nothing to commit. Add something first"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testCommitShouldCommitStagedFilesAndUpdateBranchRef(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
            Files.createDirectories(tempDir.resolve(".glit/objects"));
            Files.createFile(tempDir.resolve(".glit/refs/heads/main"));
            Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");

            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");

            Path file = tempDir.resolve("file.txt");
            Files.writeString(file, "hello");

            Call call = new Call("add", null, List.of(file));
            Repository.add(call);

            Call commitCall = new Call("commit", List.of("-m"), List.of("initial commit"));
            Repository.commit(commitCall);

            String branchContent = Files.readString(tempDir.resolve(".glit/refs/heads/main")).trim();
            assertFalse(branchContent.isBlank(), "Branch ref should contain the new commit hash");

            Path commitObjectPath = tempDir.resolve(".glit/objects")
                    .resolve(branchContent.substring(0, 2))
                    .resolve(branchContent.substring(2));
            assertTrue(Files.exists(commitObjectPath), "Commit object should be stored in .glit/objects");

            assertEquals("ref: refs/heads/main", Files.readString(tempDir.resolve(".glit/HEAD")).trim());
            assertEquals(0, Files.size(tempDir.resolve(".glit/index")), "Index should be cleared after commit");
            assertTrue(outContent.toString().contains("[main " + branchContent.substring(0, 7) + "] initial commit"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testCommitShouldCommitInDetachedHeadModeAndUpdateHeadHash(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit"));
            Files.createDirectories(tempDir.resolve(".glit/objects"));
            Files.writeString(tempDir.resolve(".glit/HEAD"), "4a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b");

            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");

            Path file = tempDir.resolve("detached.txt");
            Files.writeString(file, "detached content");
            Repository.add(new Call("add", List.of(), List.of(file)));

            Call commitCall = new Call("commit", List.of(), List.of("detached commit"));
            Repository.commit(commitCall);

            String newHead = Files.readString(tempDir.resolve(".glit/HEAD")).trim();
            assertTrue(newHead.matches("[0-9a-f]{40}"), "HEAD should be updated to the new commit hash in detached mode");
            assertNotEquals("4a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b", newHead);
            assertTrue(outContent.toString().contains("On branch detached HEAD"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

}