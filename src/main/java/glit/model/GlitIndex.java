package glit.model;

import java.util.List;

public class GlitIndex {

    private final int version;
    private final List<IndexEntry> entries;
    private final byte[] checksum;

    public GlitIndex(int version, List<IndexEntry> entries, byte[] checksum) {
        this.version = version;
        this.entries = entries;
        this.checksum = checksum;
    }

    public GlitIndex(int version, List<IndexEntry> entries) {
        this.version = version;
        this.entries = entries;
        checksum = null;
    }

    public int getVersion() {
        return version;
    }

    public List<IndexEntry> getEntries() {
        return entries;
    }

    public byte[] getChecksum() {
        return checksum;
    }
}
