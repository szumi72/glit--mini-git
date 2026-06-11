package glit.merge;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import glit.exceptions.MergeConflictException;
import glit.model.Blob;
import glit.storage.ObjectReader;
import glit.storage.ObjectWriter;

class FileMergerTest {

    private ObjectReader readerMock;
    private ObjectWriter writerMock;
    private FileMerger fileMerger;

    private static final String HASH_BASE   = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_OURS   = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String HASH_THEIRS = "cccccccccccccccccccccccccccccccccccccccc";
    private static final String HASH_MERGED = "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm";

    @BeforeEach
    void setUp() {
        readerMock = mock(ObjectReader.class);
        writerMock = mock(ObjectWriter.class);
        fileMerger = new FileMerger(readerMock, writerMock);
    }

    private Blob blob(String content) {
        return new Blob(content.getBytes(StandardCharsets.UTF_8));
    }

    private void mockRead(String hash, Blob blob) {
        when(readerMock.readObject(hash)).thenReturn(blob);
    }

    private void mockWriteReturn(String hash) {
        when(writerMock.saveObject(any(Blob.class))).thenReturn(hash);
    }

    // ==================== Brak bazy ====================
    @Test
    void shouldReturnNullWhenBothSidesNull() {
        assertNull(fileMerger.mergeFiles(null, null, null));
    }

    @Test
    void shouldReturnOursWhenOnlyOursAdded() {
        // Nie trzeba mockować read, bo zwracamy istniejący hash
        assertEquals(HASH_OURS, fileMerger.mergeFiles(null, HASH_OURS, null));
    }

    @Test
    void shouldReturnTheirsWhenOnlyTheirsAdded() {
        assertEquals(HASH_THEIRS, fileMerger.mergeFiles(null, null, HASH_THEIRS));
    }

    @Test
    void shouldReturnSameHashWhenBothAddedIdentical() {
        assertEquals(HASH_OURS, fileMerger.mergeFiles(null, HASH_OURS, HASH_OURS));
    }

    @Test
    void shouldThrowConflictWhenBothAddedDifferent() {
        mockRead(HASH_OURS, blob("our file\n"));
        mockRead(HASH_THEIRS, blob("their file\n"));
        assertThrows(MergeConflictException.class,
                () -> fileMerger.mergeFiles(null, HASH_OURS, HASH_THEIRS));
    }

    // ==================== Z bazą – podstawowe przypadki ====================
    @Test
    void shouldReturnBaseWhenAllEqual() {
        assertEquals(HASH_BASE, fileMerger.mergeFiles(HASH_BASE, HASH_BASE, HASH_BASE));
    }

    @Test
    void shouldReturnOursWhenOnlyOursChanged() {
        // Tylko our zmienił – wystarczy zwrócić jego hash
        assertEquals(HASH_OURS, fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_BASE));
    }

    @Test
    void shouldReturnTheirsWhenOnlyTheirsChanged() {
        assertEquals(HASH_THEIRS, fileMerger.mergeFiles(HASH_BASE, HASH_BASE, HASH_THEIRS));
    }

    @Test
    void shouldReturnOursWhenBothMadeSameChange() {
        // Obie strony zmieniły w ten sam sposób – zwracamy ours
        assertEquals(HASH_OURS, fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_OURS));
    }

    // ==================== Automatyczne scalanie bez konfliktu ====================
    @Test
    void shouldMergeNonConflictingAdditions() {
        mockRead(HASH_BASE, blob("line1\n"));
        mockRead(HASH_OURS, blob("line1\nline2\n"));
        mockRead(HASH_THEIRS, blob("line1\nline3\n"));
        mockWriteReturn(HASH_MERGED);

        String result = fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_THEIRS);
        assertEquals(HASH_MERGED, result);

        ArgumentCaptor<Blob> captor = ArgumentCaptor.forClass(Blob.class);
        verify(writerMock).saveObject(captor.capture());
        String mergedText = new String(captor.getValue().getContent(), StandardCharsets.UTF_8);
        assertTrue(mergedText.contains("line2") && mergedText.contains("line3"));
        assertFalse(mergedText.contains("<<<<<<<"));
    }

    // ==================== Konflikty (rzucanie wyjątku) ====================
    @Test
    void shouldThrowConflictWhenBothModifySameLineDifferently() {
        mockRead(HASH_BASE, blob("original\n"));
        mockRead(HASH_OURS, blob("ours changed\n"));
        mockRead(HASH_THEIRS, blob("theirs changed\n"));
        assertThrows(MergeConflictException.class,
                () -> fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_THEIRS));
    }

    @Test
    void shouldTakeOursWhenTheirsDeleted() {
        // ours modyfikuje, theirs usuwa (null) – wybieramy ours
        mockRead(HASH_BASE, blob("base\n"));
        mockRead(HASH_OURS, blob("ours modified\n"));
        // theirsHash = null
        assertEquals(HASH_OURS, fileMerger.mergeFiles(HASH_BASE, HASH_OURS, null));
    }

    @Test
    void shouldReturnNullWhenBothSidesDelete() {
        assertNull(fileMerger.mergeFiles(HASH_BASE, null, null));
    }

    @Test
    void shouldMergeEmptyBaseWithIdenticalAdditions() {
        mockRead(HASH_BASE, blob(""));
        mockRead(HASH_OURS, blob("same\n"));
        mockRead(HASH_THEIRS, blob("same\n"));
        mockWriteReturn(HASH_MERGED);
        assertEquals(HASH_MERGED, fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_THEIRS));
    }
}