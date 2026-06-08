package glit.merge;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import glit.exceptions.MergeConflictException;
import glit.model.Blob;
import glit.storage.ObjectReader;
import glit.storage.ObjectWriter;

/**
 * Performs a line-by-line three-way merge on the contents of {@link Blob} objects.
 * <p>
 * This class compares a common base version of a file with two modified versions
 * ("ours" and "theirs"). It uses independent line pointers to maintain synchronization
 * and automatically resolve additions or modifications where possible.
 * If conflicting changes are detected on the same line, a {@link MergeConflictException} is thrown.
 * </p>
 */
public class FileMerger {

    /** Object reader used to load the content of the base, ours, and theirs blobs. */
    private final ObjectReader reader;

    /** Object writer used to persist the newly merged blob object. */
    private final ObjectWriter writer;

    /**
     * Constructs a new {@code FileMerger} with the specified reader and writer components.
     *
     * @param reader the object reader used to look up existing blobs by hash
     * @param writer the object writer used to save the merged blob result
     */
    public FileMerger(ObjectReader reader, ObjectWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    /**
     * Merges three versions of a file and returns the SHA-1 hash of the resulting merged blob.
     * <p>
     * The method first attempts fast-path resolution (e.g., checking if only one side modified 
     * the file or if a file was deleted vs modified). If both sides modified the file, it falls 
     * back to a line-by-line pointer synchronization algorithm to merge the text contents.
     * </p>
     *
     * @param baseHash   the hash of the common ancestor blob (may be {@code null} if the file was newly added)
     * @param oursHash   the hash of "our" version of the blob (may be {@code null} if deleted)
     * @param theirsHash the hash of "their" version of the blob (may be {@code null} if deleted)
     * @return the SHA-1 hash of the created and persisted merged blob object, or {@code null} if the file is removed
     * @throws MergeConflictException if automatic line-by-line resolution fails due to conflicting edits
     */
    public String mergeFiles(String baseHash, String oursHash, String theirsHash) {
        // Fast-path: All three versions are identical
        if (baseHash != null && baseHash.equals(oursHash) && baseHash.equals(theirsHash)) {
            return baseHash;
        }

        // Fast-path: No common base (handling file additions in one or both branches)
        if (baseHash == null) {
            if (oursHash != null && theirsHash != null && !oursHash.equals(theirsHash)) {
                List<String> oursLines = readLinesFromBlob(oursHash);
                List<String> theirsLines = readLinesFromBlob(theirsHash);
                if (!oursLines.equals(theirsLines)) {
                    throw new MergeConflictException("Both added file with different content");
                }
            }
            if (oursHash != null) return oursHash;
            if (theirsHash != null) return theirsHash;
            return null;
        }

        // Fast-path: Both sides made the exact same modification
        if (oursHash != null && theirsHash != null && oursHash.equals(theirsHash)) {
            return oursHash;
        }

        // Fast-path: Only we modified the file (theirs remains matching base)
        if (theirsHash != null && theirsHash.equals(baseHash) && oursHash != null && !oursHash.equals(baseHash)) {
            return oursHash;
        }

        // Fast-path: Only they modified the file (ours remains matching base)
        if (oursHash != null && oursHash.equals(baseHash) && theirsHash != null && !theirsHash.equals(baseHash)) {
            return theirsHash;
        }

        // Fast-path: Handling file deletion vs file modification
        if (oursHash == null && theirsHash != null && !theirsHash.equals(baseHash)) {
            return theirsHash;
        }
        if (oursHash != null && theirsHash == null && !oursHash.equals(baseHash)) {
            return oursHash;
        }

        // Read textual content line-by-line from storage
        List<String> base   = readLinesFromBlob(baseHash);
        List<String> ours   = readLinesFromBlob(oursHash);
        List<String> theirs = readLinesFromBlob(theirsHash);

        List<String> merged = new ArrayList<>();
        int bIdx = 0, oIdx = 0, tIdx = 0;

        // Iterate while there are remaining lines to process in any of the files
        while (bIdx < base.size() || oIdx < ours.size() || tIdx < theirs.size()) {
            String bLine = (bIdx < base.size()) ? base.get(bIdx) : null;
            String oLine = (oIdx < ours.size()) ? ours.get(oIdx) : null;
            String tLine = (tIdx < theirs.size()) ? theirs.get(tIdx) : null;

            // 1. Common line: unchanged in both branches, take it and advance all pointers
            if (bLine != null && bLine.equals(oLine) && bLine.equals(tLine)) {
                merged.add(bLine);
                bIdx++; oIdx++; tIdx++;
                continue;
            }

            // 2. OURS added/modified a line: theirs still matches base, consume from ours only
            if (oLine != null && !oLine.equals(bLine) && bLine != null && bLine.equals(tLine)) {
                merged.add(oLine);
                oIdx++;
                continue;
            }

            // 3. THEIRS added/modified a line: ours still matches base, consume from theirs only
            if (tLine != null && !tLine.equals(bLine) && bLine != null && bLine.equals(oLine)) {
                merged.add(tLine);
                tIdx++;
                continue;
            }

            // 4. Content conflict: both sides modified the exact same base line differently
            if (bLine != null && oLine != null && tLine != null &&
                !bLine.equals(oLine) && !bLine.equals(tLine)) {
                if (oLine.equals(tLine)) {
                    merged.add(oLine); // Both made the same change
                } else {
                    throw new MergeConflictException("Conflict: both sides modified the same line differently");
                }
                bIdx++; oIdx++; tIdx++;
                continue;
            }

            // 5. Append lines: base has ended, but both sides appended lines to the end of the file
            if (bLine == null && oLine != null && tLine != null) {
                merged.add(oLine);
                merged.add(tLine);
                oIdx++; tIdx++;
                continue;
            }

            // 6. Fallback path: handles structural divergences (e.g., line deletion vs line modification)
            if (oLine != null || tLine != null) {
                if (oLine != null && tLine != null && !oLine.equals(tLine)) {
                    throw new MergeConflictException("Conflict: divergent changes");
                } else if (oLine != null) {
                    merged.add(oLine);
                    oIdx++;
                } else {
                    merged.add(tLine);
                    tIdx++;
                }
                if (bLine != null) bIdx++;
            } else {
                if (bLine != null) bIdx++;
            }
        }

        // Combine lines and write the newly merged content back to storage as a Blob
        String mergedText = String.join("\n", merged);
        return writer.saveObject(new Blob(mergedText.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Reads the binary content of a blob and splits it into a list of UTF-8 strings representing lines.
     *
     * @param hash the SHA-1 hash of the blob object to read
     * @return a {@link List} of strings containing the file lines; empty if hash is {@code null} or blob is missing
     */
    private List<String> readLinesFromBlob(String hash) {
        if (hash == null) return Collections.emptyList();
        Blob blob = (Blob) reader.readObject(hash);
        if (blob == null || blob.getContent() == null) return Collections.emptyList();
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(blob.getContent()), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.toList());
    }
}