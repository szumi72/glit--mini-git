// package glit.merge;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.Mockito.*;

// import java.nio.charset.StandardCharsets;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;

// import glit.exceptions.MergeConflictException;
// import glit.model.Blob;
// import glit.storage.ObjectReader;
// import glit.storage.ObjectWriter;

// class FileMergerTest {

//     private ObjectReader readerMock;
//     private ObjectWriter writerMock;
//     private FileMerger fileMerger;

//     private static final String HASH_BASE   = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
//     private static final String HASH_OURS   = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
//     private static final String HASH_THEIRS = "cccccccccccccccccccccccccccccccccccccccc";
//     private static final String HASH_MERGED = "mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm";

//     @BeforeEach
//     void setUp() {
//         readerMock = mock(ObjectReader.class);
//         writerMock = mock(ObjectWriter.class);
//         fileMerger = new FileMerger(readerMock, writerMock);
//     }

//     private Blob blob(String content) {
//         return new Blob(content.getBytes(StandardCharsets.UTF_8));
//     }

//     private void mockRead(String hash, Blob blob) {
//         when(readerMock.readObject(hash)).thenReturn(blob);
//     }

//     private void mockWriteReturn(String hash) {
//         when(writerMock.saveObject(any(Blob.class))).thenReturn(hash);
//     }

//     // ==================== Brak bazy (dodawanie nowego pliku) ====================
//     @Test
//     void shouldReturnNullWhenBothSidesNull() {
//         assertNull(fileMerger.mergeFiles(null, null, null));
//     }

//     @Test
//     void shouldReturnOursWhenOnlyOursAdded() {
//         mockRead(HASH_OURS, blob("our content\n"));
//         mockWriteReturn(HASH_MERGED);
//         assertEquals(HASH_MERGED, fileMerger.mergeFiles(null, HASH_OURS, null));
//     }

//     @Test
//     void shouldReturnTheirsWhenOnlyTheirsAdded() {
//         mockRead(HASH_THEIRS, blob("their content\n"));
//         mockWriteReturn(HASH_MERGED);
//         assertEquals(HASH_MERGED, fileMerger.mergeFiles(null, null, HASH_THEIRS));
//     }

//     @Test
//     void shouldReturnSameHashWhenBothAddedIdentical() {
//         // Ten sam hash – nie trzeba nic czytać
//         assertEquals(HASH_OURS, fileMerger.mergeFiles(null, HASH_OURS, HASH_OURS));
//     }

//     @Test
//     void shouldThrowConflictWhenBothAddedDifferent() {
//         mockRead(HASH_OURS, blob("our file\n"));
//         mockRead(HASH_THEIRS, blob("their file\n"));
//         assertThrows(MergeConflictException.class,
//                 () -> fileMerger.mergeFiles(null, HASH_OURS, HASH_THEIRS));
//     }

//     // ==================== Z bazą – podstawowe przypadki ====================
//     @Test
//     void shouldReturnBaseWhenAllEqual() {
//         assertEquals(HASH_BASE, fileMerger.mergeFiles(HASH_BASE, HASH_BASE, HASH_BASE));
//     }

//     @Test
//     void shouldTakeOursWhenOnlyOursChanged() {
//         mockRead(HASH_BASE, blob("base\n"));
//         mockRead(HASH_OURS, blob("ours\n"));
//         mockWriteReturn(HASH_MERGED);
//         assertEquals(HASH_MERGED, fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_BASE));
//     }

//     @Test
//     void shouldTakeTheirsWhenOnlyTheirsChanged() {
//         mockRead(HASH_BASE, blob("base\n"));
//         mockRead(HASH_THEIRS, blob("theirs\n"));
//         mockWriteReturn(HASH_MERGED);
//         assertEquals(HASH_MERGED, fileMerger.mergeFiles(HASH_BASE, HASH_BASE, HASH_THEIRS));
//     }

//     // ==================== Automatyczne scalanie bez konfliktu ====================
//     @Test
//     void shouldMergeNonConflictingAdditions() {
//         // Baza: line1
//         // Ours: line1 + line2 (dodaliśmy linię)
//         // Theirs: line1 + line3 (oni dodali linię)
//         mockRead(HASH_BASE, blob("line1\n"));
//         mockRead(HASH_OURS, blob("line1\nline2\n"));
//         mockRead(HASH_THEIRS, blob("line1\nline3\n"));
//         mockWriteReturn(HASH_MERGED);

//         String result = fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_THEIRS);
//         assertEquals(HASH_MERGED, result);

//         // Sprawdzamy, czy scalony blob zawiera obie dodane linie, bez znaczników
//         ArgumentCaptor<Blob> captor = ArgumentCaptor.forClass(Blob.class);
//         verify(writerMock).saveObject(captor.capture());
//         String mergedText = new String(captor.getValue().getContent(), StandardCharsets.UTF_8);
//         assertTrue(mergedText.contains("line2") && mergedText.contains("line3"));
//         assertFalse(mergedText.contains("<<<<<<<"));
//     }

//     @Test
//     void shouldMergeWhenBothMadeSameChange() {
//         // Obie strony zmieniły tę samą linię w ten sam sposób
//         mockRead(HASH_BASE, blob("old\n"));
//         mockRead(HASH_OURS, blob("new\n"));
//         mockRead(HASH_THEIRS, blob("new\n"));
//         mockWriteReturn(HASH_MERGED);
//         assertEquals(HASH_MERGED, fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_THEIRS));
//     }

//     // ==================== Konflikty (oczekiwane wyjątki) ====================
//     @Test
//     void shouldThrowConflictWhenBothModifySameLineDifferently() {
//         mockRead(HASH_BASE, blob("original\n"));
//         mockRead(HASH_OURS, blob("ours changed\n"));
//         mockRead(HASH_THEIRS, blob("theirs changed\n"));
//         assertThrows(MergeConflictException.class,
//                 () -> fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_THEIRS));
//     }

//     @Test
//     void shouldThrowConflictOnDeleteVsModify() {
//         // ours modyfikuje, theirs usuwa (null)
//         mockRead(HASH_BASE, blob("base\n"));
//         mockRead(HASH_OURS, blob("ours modified\n"));
//         // theirs = null
//         assertThrows(MergeConflictException.class,
//                 () -> fileMerger.mergeFiles(HASH_BASE, HASH_OURS, null));
//     }

//     @Test
//     void shouldReturnNullWhenBothSidesDelete() {
//         assertNull(fileMerger.mergeFiles(HASH_BASE, null, null));
//     }

//     @Test
//     void shouldMergeEmptyBaseWithAdditions() {
//         // Pusta baza, obie strony dodały inne linie – algorytm może nie wykryć konfliktu,
//         // ale według aktualnej logiki powinien rzucić wyjątek, jeśli linie są różne.
//         // Tutaj używamy identycznych treści, więc powinno przejść.
//         mockRead(HASH_BASE, blob(""));
//         mockRead(HASH_OURS, blob("ours\n"));
//         mockRead(HASH_THEIRS, blob("ours\n")); // identyczne
//         mockWriteReturn(HASH_MERGED);
//         String result = fileMerger.mergeFiles(HASH_BASE, HASH_OURS, HASH_THEIRS);
//         assertEquals(HASH_MERGED, result);
//     }
// }