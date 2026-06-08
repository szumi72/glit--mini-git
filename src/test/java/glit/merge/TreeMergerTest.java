package glit.merge;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import glit.exceptions.MergeConflictException;
import glit.model.Tree;
import glit.model.TreeEntry;
import glit.storage.ObjectReader;
import glit.storage.ObjectWriter;

class TreeMergerTest {

    @TempDir
    Path tempDir;

    private Path repoPath;
    private ObjectReader readerMock;
    private ObjectWriter writerMock;
    private TreeMerger merger;

    // 40-znakowe testowe hashe (bezpieczne dla HexUtils)
    private static final String HASH_BASE     = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_OURS     = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String HASH_THEIRS   = "cccccccccccccccccccccccccccccccccccccccc";
    private static final String HASH_BASESUB  = "1111111111111111111111111111111111111111";
    private static final String HASH_OURSSUB  = "2222222222222222222222222222222222222222";
    private static final String HASH_THEIRSSUB= "3333333333333333333333333333333333333333";
    private static final String HASH_MERGED_SUB = "ssssssssssssssssssssssssssssssssssssssss";
    private static final String HASH_MERGED_TOP = "tttttttttttttttttttttttttttttttttttttttt";

    @BeforeEach
    void setUp() throws Exception {
        repoPath = tempDir;
        Files.createDirectories(repoPath.resolve(".glit"));

        readerMock = mock(ObjectReader.class);
        writerMock = mock(ObjectWriter.class);

        merger = new TreeMerger(repoPath);
        setField(merger, "reader", readerMock);
        setField(merger, "writer", writerMock);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private TreeEntry blob(String hash, String name) {
        return new TreeEntry("100644", hash, name);
    }

    private TreeEntry tree(String hash, String name) {
        return new TreeEntry("040000", hash, name);
    }

    // Modyfikowalna lista 
    private Tree tree(TreeEntry... entries) {
        return new Tree(new ArrayList<>(List.of(entries)));
    }

    // ====================== Podstawowe akcje ======================

    @Test
    void shouldReturnBaseHashWhenAllNull() {
        assertNull(merger.mergeTree(null, null, null));
    }

    @Test
    void shouldReturnBaseHashWhenAllSame() {
        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(HASH_BASE, "a.txt")));
        assertEquals(HASH_BASE, merger.mergeTree(HASH_BASE, HASH_BASE, HASH_BASE));
    }

    @Test
    void shouldTakeOursWhenOnlyOursChanged() {
        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(HASH_BASE, "file")));
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(HASH_OURS, "file")));
        assertEquals(HASH_OURS, merger.mergeTree(HASH_BASE, HASH_OURS, HASH_BASE));
    }

    @Test
    void shouldTakeTheirsWhenOnlyTheirsChanged() {
        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(HASH_BASE, "file")));
        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(HASH_THEIRS, "file")));
        assertEquals(HASH_THEIRS, merger.mergeTree(HASH_BASE, HASH_BASE, HASH_THEIRS));
    }

    @Test
    void shouldTakeOursWhenBothChangedButIdentical() {
        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(HASH_BASE, "file")));
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(HASH_OURS, "file")));
        assertEquals(HASH_OURS, merger.mergeTree(HASH_BASE, HASH_OURS, HASH_OURS));
    }

    // ====================== Konflikty ======================

    @Test
    void shouldThrowConflictWhenBothBlobsChangedDifferently() {
        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(HASH_BASE, "file.txt")));
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(HASH_OURS, "file.txt")));
        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(HASH_THEIRS, "file.txt")));

        assertThrows(MergeConflictException.class,
                () -> merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS));
    }

    @Test
    void shouldThrowStructuralConflictWhenOursIsTreeAndTheirsIsBlob() {
        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(tree(HASH_BASESUB, "dir")));
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(tree(HASH_OURSSUB, "dir")));
        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(HASH_THEIRSSUB, "dir")));

        assertThrows(MergeConflictException.class,
                () -> merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS));
    }

    @Test
    void shouldThrowConflictOnDeletionVersusModification() {
        // Plik usunięty w ours, zmodyfikowany w theirs – powinno być konfliktem
        when(readerMock.readObject(HASH_BASE)).thenReturn(tree(blob(HASH_BASE, "file.txt")));
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree());               // puste
        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(HASH_THEIRS, "file.txt")));

        assertThrows(MergeConflictException.class,
                () -> merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS));
    }

    // ====================== Dodawanie nowych plików ======================

    @Test
    void shouldAddNewEntryFromOurs() {
        // Tylko our dodało nowy plik – wynik to wersja z our
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(HASH_OURS, "newfile.txt")));
        assertEquals(HASH_OURS, merger.mergeTree(null, HASH_OURS, null));
    }

    @Test
    void shouldAddSameEntryWhenBothAddedIdentical() {
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(HASH_OURS, "newfile.txt")));
        assertEquals(HASH_OURS, merger.mergeTree(null, HASH_OURS, HASH_OURS));
    }

    @Test
    void shouldThrowConflictWhenBothAddedDifferentBlobs() {
        when(readerMock.readObject(HASH_OURS)).thenReturn(tree(blob(HASH_OURS, "file.txt")));
        when(readerMock.readObject(HASH_THEIRS)).thenReturn(tree(blob(HASH_THEIRS, "file.txt")));

        assertThrows(MergeConflictException.class,
                () -> merger.mergeTree(null, HASH_OURS, HASH_THEIRS));
    }

    // ====================== Rekurencyjne scalanie ======================

    @Test
    void shouldRecursivelyMergeWhenBothSidesModifySameSubtreeWithoutConflict() {
        // Drzewo główne: każde ma podkatalog "dir"
        Tree baseTop = tree(tree(HASH_BASESUB, "dir"));
        Tree oursTop = tree(tree(HASH_OURSSUB, "dir"));
        Tree theirsTop = tree(tree(HASH_THEIRSSUB, "dir"));

        // Wewnątrz "dir":
        // base: plik f1
        Tree baseSub = tree(blob(HASH_BASE, "f1"));
        // ours: zmieniony f1 + nowy f2
        Tree oursSub = tree(blob(HASH_OURS, "f1"), blob(HASH_OURS, "f2"));
        // theirs: f1 bez zmian + nowy f3
        Tree theirsSub = tree(blob(HASH_BASE, "f1"), blob(HASH_THEIRS, "f3"));

        // Rejestrujemy mocki
        when(readerMock.readObject(HASH_BASE)).thenReturn(baseTop);
        when(readerMock.readObject(HASH_OURS)).thenReturn(oursTop);
        when(readerMock.readObject(HASH_THEIRS)).thenReturn(theirsTop);
        when(readerMock.readObject(HASH_BASESUB)).thenReturn(baseSub);
        when(readerMock.readObject(HASH_OURSSUB)).thenReturn(oursSub);
        when(readerMock.readObject(HASH_THEIRSSUB)).thenReturn(theirsSub);

        // Oczekujemy, że zostanie zapisane scalone poddrzewo i potem główne drzewo
        when(writerMock.saveObject(any(Tree.class)))
                .thenReturn(HASH_MERGED_SUB)   // pierwsze wywołanie – dla poddrzewa
                .thenReturn(HASH_MERGED_TOP);  // drugie – dla głównego

        String result = merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS);
        assertEquals(HASH_MERGED_TOP, result);
    }

    @Test
    void shouldRecursivelyMergeAndDetectConflictInSubtree() {
        // Podobna struktura, ale wewnątrz poddrzewa konflikt (oba zmieniły ten sam plik inaczej)
        Tree baseTop = tree(tree(HASH_BASESUB, "dir"));
        Tree oursTop = tree(tree(HASH_OURSSUB, "dir"));
        Tree theirsTop = tree(tree(HASH_THEIRSSUB, "dir"));

        Tree baseSub = tree(blob(HASH_BASE, "f1"));
        Tree oursSub = tree(blob(HASH_OURS, "f1"));       // zmiana
        Tree theirsSub = tree(blob(HASH_THEIRS, "f1"));   // inna zmiana

        when(readerMock.readObject(HASH_BASE)).thenReturn(baseTop);
        when(readerMock.readObject(HASH_OURS)).thenReturn(oursTop);
        when(readerMock.readObject(HASH_THEIRS)).thenReturn(theirsTop);
        when(readerMock.readObject(HASH_BASESUB)).thenReturn(baseSub);
        when(readerMock.readObject(HASH_OURSSUB)).thenReturn(oursSub);
        when(readerMock.readObject(HASH_THEIRSSUB)).thenReturn(theirsSub);

        assertThrows(MergeConflictException.class,
                () -> merger.mergeTree(HASH_BASE, HASH_OURS, HASH_THEIRS));
    }
}