//package glit.merge;
//
//import java.lang.reflect.Field;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//import glit.exceptions.MergeConflictException;
//import glit.model.Blob;
//import glit.model.Tree;
//import glit.model.TreeEntry;
//import glit.storage.ObjectReader;
//import glit.storage.ObjectWriter;
//
//class TreeMergerTest {
//
//    @TempDir
//    Path tempDir;
//
//    private Path repoPath;
//    private ObjectReader readerMock;
//    private ObjectWriter writerMock;
//    private TreeMerger merger;
//
//    // Hashe drzew
//    private static final String HASH_BASE       = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
//    private static final String HASH_OURS       = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
//    private static final String HASH_THEIRS     = "cccccccccccccccccccccccccccccccccccccccc";
//    private static final String HASH_BASESUB    = "1111111111111111111111111111111111111111";
//    private static final String HASH_OURSSUB    = "2222222222222222222222222222222222222222";
//    private static final String HASH_THEIRSSUB  = "3333333333333333333333333333333333333333";
//    private static final String HASH_MERGED_SUB = "ssssssssssssssssssssssssssssssssssssssss";
//    private static final String HASH_MERGED_TOP = "tttttttttttttttttttttttttttttttttttttttt";
//
//    // Hashe blobów – zupełnie inne, by nie nadpisywać mocków drzew
//    private static final String BLOB_BASE   = "b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1";
//    private static final String BLOB_OURS   = "b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2";
//    private static final String BLOB_THEIRS = "b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3";
//    private static final String BLOB_MERGED = "b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4";
//
//    @BeforeEach
//    void setUp() throws Exception {
//        repoPath = tempDir;
//        Files.createDirectories(repoPath.resolve(".glit"));
//
//        readerMock = mock(ObjectReader.class);
//        writerMock = mock(ObjectWriter.class);
//
//        merger = new TreeMerger(repoPath);
//        setField(merger, "reader", readerMock);
//        setField(merger, "writer", writerMock);
//    }
//
//    private void setField(Object target, String fieldName, Object value) throws Exception {
//        Field field = target.getClass().getDeclaredField(fieldName);
//        field.setAccessible(true);
//        field.set(target, value);
//    }
//
//    private TreeEntry blob(String hash, String name) {
//        return new TreeEntry("100644", hash, name);
//    }
//
//    private TreeEntry tree(String hash, String name) {
//        return new TreeEntry("040000", hash, name);
//    }
//
//    private Tree tree(TreeEntry... entries) {
//        return new Tree(new ArrayList<>(List.of(entries)));
//    }
//
//    // ==================== Podstawowe akcje ====================
//    @Test
//    void shouldReturnBaseHashWhenAllNull() {
//        assertNull(merger.mergeTree(null, null, null));
//    }
//
//    @Test
//    void shouldReturnBaseHashWhenAllSame() {
//        assertEquals(HASH_BASE, merger.mergeTree(HASH_BASE, HASH_BASE, HASH_BASE));
//    }
//
//    @Test
//    void shouldTakeOursWhenOnlyOursChanged() {
//        assertEquals(HASH_OURS, merger.mergeTree(HASH_BASE, HASH_OURS, HASH_BASE));
//    }
//
//    @Test
//    void shouldTakeTheirsWhenOnlyTheirsChanged() {
//        assertEquals(HASH_THEIRS, merger.mergeTree(HASH_BASE, HASH_BASE, HASH_THEIRS));
//    }
//
//    @Test
//    void shouldTakeOursWhenBothChangedButIdentical() {
//        assertEquals(HASH_OURS, merger.mergeTree(HASH_BASE, HASH_OURS, HASH_OURS));
//    }
//
//    @Test
//    void shouldAddNewEntryFromOurs() {
//        assertEquals(HASH_OURS, merger.mergeTree(null, HASH_OURS, null));
//    }
//
//    @Test
//    void shouldAddSameEntryWhenBothAddedIdentical() {
//        assertEquals(HASH_OURS, merger.mergeTree(null, HASH_OURS, HASH_OURS));
//    }
//
//    // ==================== Konflikty strukturalne ====================
//    @Test
//    void shouldThrowStructuralConflictWhenOursIsTreeAndTheirsIsBlob() {
//        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(tree(HASH_BASESUB, "dir")));
//        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(tree(HASH_OURSSUB, "dir")));
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(HASH_THEIRSSUB, "dir")));
//
//        assertThrows(MergeConflictException.class,
//                () -> merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS));
//    }
//
//   @Test
//        void shouldTakeTheirsWhenOursDeletesAndTheirsModifies() {
//        // Base: plik file.txt z BLOB_BASE
//        // Ours: puste drzewo (usunięcie)
//        // Theirs: plik file.txt z BLOB_THEIRS (zmodyfikowany)
//        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(BLOB_BASE, "file.txt")));
//        when(readerMock.readObject(HASH_OURS)).thenReturn(tree()); // usunięcie
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(BLOB_THEIRS, "file.txt")));
//
//        // Oczekujemy zapisu nowego drzewa (z plikiem od theirs)
//        when(writerMock.saveObject(any(Tree.class))).thenReturn(HASH_MERGED_TOP);
//
//        String result = merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS);
//        assertEquals(HASH_MERGED_TOP, result);
//        }
//
//        @Test
//        void shouldTakeOursWhenTheirsDeletesAndOursModifies() {
//        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(BLOB_BASE, "file.txt")));
//        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(BLOB_OURS, "file.txt")));
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree()); // usunięcie w theirs
//
//        when(writerMock.saveObject(any(Tree.class))).thenReturn(HASH_MERGED_TOP);
//
//        String result = merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS);
//        assertEquals(HASH_MERGED_TOP, result);
//        }
//
//    // ==================== Konflikty blobów (FileMerger rzuca wyjątek) ====================
//    @Test
//    void shouldThrowConflictWhenBothBlobsChangedDifferently() {
//        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(BLOB_BASE, "file.txt")));
//        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(BLOB_OURS, "file.txt")));
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(BLOB_THEIRS, "file.txt")));
//
//        when(readerMock.readObject(BLOB_BASE)).thenReturn(new Blob("line1\n".getBytes()));
//        when(readerMock.readObject(BLOB_OURS)).thenReturn(new Blob("ours changed\n".getBytes()));
//        when(readerMock.readObject(BLOB_THEIRS)).thenReturn(new Blob("theirs changed\n".getBytes()));
//
//        assertThrows(MergeConflictException.class,
//                () -> merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS));
//    }
//
//    @Test
//    void shouldThrowConflictWhenBothAddedDifferentBlobs() {
//        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(BLOB_OURS, "newfile.txt")));
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(BLOB_THEIRS, "newfile.txt")));
//
//        when(readerMock.readObject(BLOB_OURS)).thenReturn(new Blob("our file\n".getBytes()));
//        when(readerMock.readObject(BLOB_THEIRS)).thenReturn(new Blob("their file\n".getBytes()));
//
//        assertThrows(MergeConflictException.class,
//                () -> merger.mergeTree(null, HASH_OURS, HASH_THEIRS));
//    }
//
//    // ==================== Automatyczne scalenie blobów (bez konfliktu) ====================
//    @Test
//    void shouldAutoMergeBlobsWithoutConflict() {
//        // Drzewa
//        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(BLOB_BASE, "file.txt")));
//        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(BLOB_OURS, "file.txt")));
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(BLOB_THEIRS, "file.txt")));
//
//        // Bloby
//        when(readerMock.readObject(BLOB_BASE)).thenReturn(new Blob("line1\n".getBytes()));
//        when(readerMock.readObject(BLOB_OURS)).thenReturn(new Blob("line1\nline2\n".getBytes()));
//        when(readerMock.readObject(BLOB_THEIRS)).thenReturn(new Blob("line1\nline3\n".getBytes()));
//
//        when(writerMock.saveObject(any(Blob.class))).thenReturn(BLOB_MERGED);
//        when(writerMock.saveObject(any(Tree.class))).thenReturn(HASH_MERGED_TOP);
//
//        String result = merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS);
//        assertEquals(HASH_MERGED_TOP, result);
//    }
//
//    // ==================== Rekurencyjne scalanie poddrzew ====================
//    @Test
//    void shouldRecursivelyMergeWhenBothSidesModifySameSubtreeWithoutConflict() {
//        Tree baseTop = tree(tree(HASH_BASESUB, "dir"));
//        Tree oursTop = tree(tree(HASH_OURSSUB, "dir"));
//        Tree theirsTop = tree(tree(HASH_THEIRSSUB, "dir"));
//
//        Tree baseSub = tree(blob(HASH_BASE, "f1"));           // tu blob o hashu HASH_BASE? lepiej użyć osobnego
//        // Używamy osobnych hashy dla plików w poddrzewie, aby nie mieszały się z hashami drzew
//        // Dla uproszczenia możemy użyć tych samych co BLOB_BASE itp., pod warunkiem że nie kolidują
//        // Zastosujemy nowe stałe BLOB_BASE1, BLOB_OURS1 itp., ale żeby nie komplikować,
//        // możemy wykorzystać BLOB_BASE, BLOB_OURS, BLOB_THEIRS (są unikalne).
//        Tree baseSubTree = tree(blob(BLOB_BASE, "f1"));
//        Tree oursSubTree = tree(blob(BLOB_OURS, "f1"), blob(BLOB_OURS, "f2"));
//        Tree theirsSubTree = tree(blob(BLOB_BASE, "f1"), blob(BLOB_THEIRS, "f3"));
//
//        when(readerMock.readObject(HASH_BASE)).thenReturn(baseTop);
//        when(readerMock.readObject(HASH_OURS)).thenReturn(oursTop);
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(theirsTop);
//        when(readerMock.readObject(HASH_BASESUB)).thenReturn(baseSubTree);
//        when(readerMock.readObject(HASH_OURSSUB)).thenReturn(oursSubTree);
//        when(readerMock.readObject(HASH_THEIRSSUB)).thenReturn(theirsSubTree);
//
//        // Bloby nie są potrzebne, bo w poddrzewie zmiany dotyczą tylko struktury (dodanie f2 i f3),
//        // a nie treści tych samych plików, więc FileMerger nie będzie wywoływany.
//        // Tylko rekurencyjne scalenie drzew.
//
//        when(writerMock.saveObject(any(Tree.class)))
//                .thenReturn(HASH_MERGED_SUB)
//                .thenReturn(HASH_MERGED_TOP);
//
//        String result = merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS);
//        assertEquals(HASH_MERGED_TOP, result);
//    }
//
//    @Test
//    void shouldRecursivelyMergeAndDetectConflictInSubtree() {
//        Tree baseTop = tree(tree(HASH_BASESUB, "dir"));
//        Tree oursTop = tree(tree(HASH_OURSSUB, "dir"));
//        Tree theirsTop = tree(tree(HASH_THEIRSSUB, "dir"));
//
//        Tree baseSubTree = tree(blob(BLOB_BASE, "f1"));
//        Tree oursSubTree = tree(blob(BLOB_OURS, "f1"));
//        Tree theirsSubTree = tree(blob(BLOB_THEIRS, "f1"));
//
//        when(readerMock.readObject(HASH_BASE)).thenReturn(baseTop);
//        when(readerMock.readObject(HASH_OURS)).thenReturn(oursTop);
//        when(readerMock.readObject(HASH_THEIRS)).thenReturn(theirsTop);
//        when(readerMock.readObject(HASH_BASESUB)).thenReturn(baseSubTree);
//        when(readerMock.readObject(HASH_OURSSUB)).thenReturn(oursSubTree);
//        when(readerMock.readObject(HASH_THEIRSSUB)).thenReturn(theirsSubTree);
//
//        // Bloby potrzebne do FileMerger
//        when(readerMock.readObject(BLOB_BASE)).thenReturn(new Blob("original\n".getBytes()));
//        when(readerMock.readObject(BLOB_OURS)).thenReturn(new Blob("ours changed\n".getBytes()));
//        when(readerMock.readObject(BLOB_THEIRS)).thenReturn(new Blob("theirs changed\n".getBytes()));
//
//        assertThrows(MergeConflictException.class,
//                () -> merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS));
//    }
//}