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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import glit.merge.TreeMerger;
import glit.model.Blob;
import glit.model.Commit;
import glit.model.GlitIndex;
import glit.model.IndexEntry;
import glit.model.Tree;
import glit.storage.ObjectReader;
import glit.util.IndexUtils;

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
            assertTrue(outContent.toString().contains("detached HEAD"));
//            assertEquals(null,outContent.toString());
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    // ==========================================
    // 5. TESTY DLA GLIT CHECKOUT
    // ==========================================

    @Test
    void testCheckoutSwitchesToExistingBranchAndRestoresRepositoryFiles(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
            Files.createFile(tempDir.resolve(".glit/index"));
            Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
            Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "commitMainHash");
            Files.writeString(tempDir.resolve(".glit/refs/heads/feature"), "commitFeatureHash");

            Path oldFile = tempDir.resolve("old.txt");
            Files.writeString(oldFile, "old content");

            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");

            Commit mockCommitFeature = mock(Commit.class);
            Commit mockCommitMain = mock(Commit.class);
            Tree mockTreeFeature = mock(Tree.class);
            Tree mockTreeMain = mock(Tree.class);
            Blob mockBlobFeature = mock(Blob.class);

            when(mockCommitFeature.getTreeHash()).thenReturn("treeFeatureHash");
            when(mockCommitMain.getTreeHash()).thenReturn("treeMainHash");
            when(mockTreeFeature.getEntries()).thenReturn(new ArrayList<>(List.of(new glit.model.TreeEntry("100644", "blobFeatureHash", "new.txt"))));
            when(mockTreeMain.getEntries()).thenReturn(new ArrayList<>(List.of(new glit.model.TreeEntry("100644", "blobMainHash", "old.txt"))));
            when(mockBlobFeature.getContent()).thenReturn("new content".getBytes());

            try (MockedConstruction<ObjectReader> mockedReader = mockConstruction(ObjectReader.class, (mock, context) -> {
                when(mock.readObject(anyString())).thenAnswer(invocation -> {
                    String hash = invocation.getArgument(0, String.class);
                    return switch (hash) {
                        case "commitFeatureHash" -> mockCommitFeature;
                        case "commitMainHash" -> mockCommitMain;
                        case "treeFeatureHash" -> mockTreeFeature;
                        case "treeMainHash" -> mockTreeMain;
                        case "blobFeatureHash" -> mockBlobFeature;
                        default -> null;
                    };
                });
            })) {
                Call call = new Call("checkout", null, List.of("feature"));
                Repository.checkout(call);
            }

            assertFalse(Files.exists(oldFile), "Old branch file should be removed after checkout");
            Path newFile = tempDir.resolve("new.txt");
            assertTrue(Files.exists(newFile), "New branch file should be restored from tree");
            assertEquals("new content", Files.readString(newFile));
            assertEquals("ref: refs/heads/feature", Files.readString(tempDir.resolve(".glit/HEAD")).trim());
            assertTrue(outContent.toString().contains("Switched to a branch 'feature'"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testCheckoutCreatesAndSwitchesToNewBranchWhenFlagBIsUsed(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
            Files.createFile(tempDir.resolve(".glit/index"));
            Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
            Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "commitMainHash");

            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");

            Call call = new Call("checkout", List.of("b"), List.of("feature"));
            Repository.checkout(call);

            assertTrue(Files.exists(tempDir.resolve(".glit/refs/heads/feature")));
            assertEquals("commitMainHash", Files.readString(tempDir.resolve(".glit/refs/heads/feature")).trim());
            assertEquals("ref: refs/heads/feature", Files.readString(tempDir.resolve(".glit/HEAD")).trim());
            assertTrue(outContent.toString().contains("Switched to a new branch 'feature'"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void testCheckoutPrintsErrorWhenIndexHasStagedFiles(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Files.createDirectories(tempDir.resolve(".glit/refs/heads"));
            Files.writeString(tempDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
            Files.writeString(tempDir.resolve(".glit/refs/heads/main"), "commitMainHash");
            Files.writeString(tempDir.resolve(".glit/refs/heads/feature"), "commitFeatureHash");
            Files.writeString(tempDir.resolve(".glit/index"), "staged content");

            Repository.REPOSITORY_PATH = tempDir;
            Repository.INDEX_PATH = tempDir.resolve(".glit/index");

            Call call = new Call("checkout", List.of(), List.of("feature"));
            Repository.checkout(call);

            assertEquals("ref: refs/heads/main", Files.readString(tempDir.resolve(".glit/HEAD")).trim());
            assertTrue(outContent.toString().contains("There are staged files. Commit them first, then checkout."));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    // ==========================================
    // 6. TESTY DLA GLIT MERGE
    // ==========================================

    @Test
    void testMergeFindsBaseTreeAndBuildsMergedTreeAndClearsIndex(@TempDir Path tempDir) throws Exception {
        String originalDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Path repoDir = tempDir;
            Files.createDirectories(repoDir.resolve(".glit/refs/heads"));
            Files.createDirectories(repoDir.resolve(".glit/objects"));
            Path indexPath = repoDir.resolve(".glit/index");
            Files.writeString(indexPath, "staged content");
            Files.writeString(repoDir.resolve(".glit/HEAD"), "ref: refs/heads/main");
            Files.writeString(repoDir.resolve(".glit/refs/heads/main"), "ourCommitHash");
            Files.writeString(repoDir.resolve(".glit/refs/heads/their"), "theirCommitHash");

            Repository.REPOSITORY_PATH = repoDir;
            Repository.INDEX_PATH = indexPath;

            Commit mockOurCommit = mock(Commit.class);
            Commit mockTheirCommit = mock(Commit.class);
            Commit mockBaseCommit = mock(Commit.class);
            Tree mockYourTree = mock(Tree.class);
            Tree mockTheirTree = mock(Tree.class);
            Tree mockBaseTree = mock(Tree.class);
            Tree mockMergedTree = mock(Tree.class);
            Blob mockMergedBlob = mock(Blob.class);

            when(mockOurCommit.getTreeHash()).thenReturn("ourTreeHash");
            when(mockOurCommit.getParentHash()).thenReturn("baseCommitHash");
            when(mockTheirCommit.getTreeHash()).thenReturn("theirTreeHash");
            when(mockTheirCommit.getParentHash()).thenReturn("baseCommitHash");
            when(mockBaseCommit.getTreeHash()).thenReturn("baseTreeHash");
            when(mockBaseCommit.getParentHash()).thenReturn("");
            when(mockYourTree.getEntries()).thenReturn(new ArrayList<>());
            when(mockTheirTree.getEntries()).thenReturn(new ArrayList<>());
            when(mockBaseTree.getEntries()).thenReturn(new ArrayList<>());
            when(mockMergedTree.getEntries()).thenReturn(new ArrayList<>(List.of(new glit.model.TreeEntry("100644", "mergedBlobHash", "merged.txt"))));
            when(mockMergedBlob.getContent()).thenReturn("merged content".getBytes());

            try (MockedConstruction<ObjectReader> mockedReader = mockConstruction(ObjectReader.class, (mock, context) -> {
                when(mock.readObject("ourCommitHash")).thenReturn(mockOurCommit);
                when(mock.readObject("theirCommitHash")).thenReturn(mockTheirCommit);
                when(mock.readObject("baseCommitHash")).thenReturn(mockBaseCommit);
                when(mock.readObject("baseTreeHash")).thenReturn(mockBaseTree);
                when(mock.readObject("ourTreeHash")).thenReturn(mockYourTree);
                when(mock.readObject("theirTreeHash")).thenReturn(mockTheirTree);
                when(mock.readObject("mergedTreeHash")).thenReturn(mockMergedTree);
                when(mock.readObject("mergedBlobHash")).thenReturn(mockMergedBlob);
            })) {
                try (MockedConstruction<TreeMerger> mockedMerger = mockConstruction(TreeMerger.class, (mock, context) -> {
                    when(mock.mergeTree("baseTreeHash", "ourTreeHash", "theirTreeHash")).thenReturn("mergedTreeHash");
                })) {
                    assertEquals("main", Repository.getCurrentBranchName(), "Merge should not run in detached HEAD mode");

                    Call call = new Call("merge", List.of(), List.of("their"));
                    Repository.merge(call);

                    assertTrue(outContent.toString().contains("Merged succesfully"), "Merge should report success");
                    verify(mockedMerger.constructed().get(0), times(1)).mergeTree("baseTreeHash", "ourTreeHash", "theirTreeHash");
                    verify(mockBaseCommit, times(1)).getTreeHash();
                }
            }

            assertEquals(0, Files.size(indexPath), "Index should be cleared after merge");
            Path mergedFile = repoDir.resolve("merged.txt");
            assertTrue(Files.exists(mergedFile), "Merged tree should restore merged file to working directory");
            assertEquals("merged content", Files.readString(mergedFile));
            assertEquals("ref: refs/heads/main", Files.readString(repoDir.resolve(".glit/HEAD")).trim());
            assertNotEquals("ourCommitHash", Files.readString(repoDir.resolve(".glit/refs/heads/main")).trim(), "Branch ref should be updated to merged commit hash");
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    // ==========================================
    // 6. TESTY DLA GLIT MERGE
    // ==========================================

}