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

public class FileMerger {

    private final ObjectReader reader;
    private final ObjectWriter writer;

    public FileMerger(ObjectReader reader, ObjectWriter writer) {
        this.reader = reader;
        this.writer = writer;
    }

    public String mergeFiles(String baseHash, String oursHash, String theirsHash) {
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

        List<String> base   = readLinesFromBlob(baseHash);
        List<String> ours   = readLinesFromBlob(oursHash);
        List<String> theirs = readLinesFromBlob(theirsHash);

        List<String> merged = new ArrayList<>();
        int bIdx = 0, oIdx = 0, tIdx = 0;

        while (bIdx < base.size() || oIdx < ours.size() || tIdx < theirs.size()) {
            String bLine = (bIdx < base.size()) ? base.get(bIdx) : null;
            String oLine = (oIdx < ours.size()) ? ours.get(oIdx) : null;
            String tLine = (tIdx < theirs.size()) ? theirs.get(tIdx) : null;

            // 1. Wspólna linia
            if (bLine != null && bLine.equals(oLine) && bLine.equals(tLine)) {
                merged.add(bLine);
                bIdx++; oIdx++; tIdx++;
                continue;
            }

            // 2. OURS dodał linię (theirs i base zsynchronizowane)
            if (oLine != null && !oLine.equals(bLine) && bLine != null && bLine.equals(tLine)) {
                merged.add(oLine);
                oIdx++;
                continue;
            }

            // 3. THEIRS dodał linię (ours i base zsynchronizowane)
            if (tLine != null && !tLine.equals(bLine) && bLine != null && bLine.equals(oLine)) {
                merged.add(tLine);
                tIdx++;
                continue;
            }

            // 4. Obie strony zmodyfikowały tę samą linię
            if (bLine != null && oLine != null && tLine != null &&
                !bLine.equals(oLine) && !bLine.equals(tLine)) {
                if (oLine.equals(tLine)) {
                    merged.add(oLine);
                } else {
                    throw new MergeConflictException("Conflict: both sides modified the same line differently");
                }
                bIdx++; oIdx++; tIdx++;
                continue;
            }

            // 5. Brak bazy (base się skończył), a obie strony mają nowe linie – dodajemy obie
            if (bLine == null && oLine != null && tLine != null) {
                merged.add(oLine);
                merged.add(tLine);
                oIdx++; tIdx++;
                continue;
            }

            // 6. Fallback – inne rozbieżności
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

        String mergedText = String.join("\n", merged);
        return writer.saveObject(new Blob(mergedText.getBytes(StandardCharsets.UTF_8)));
    }

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