package glit.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

public class GlitIndex {

    private final int version;
    private final List<IndexEntry> entries;
    private byte[] checksum;

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

    public GlitIndex(int version){
        this.version = version;
        entries = new ArrayList<>();
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

    public boolean add(IndexEntry e) {
        return entries.add(e);
    }

    public void setChecksum(byte[] checksum) {
        this.checksum = checksum;
    }

    public boolean addAll(Collection<? extends IndexEntry> c){
        return entries.addAll(c);
    }

    

}
