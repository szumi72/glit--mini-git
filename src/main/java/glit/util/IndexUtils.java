package glit.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import glit.model.GlitIndex;
import glit.model.IndexEntry;

public class IndexUtils {

    public static void write(GlitIndex index, Path indexPath) throws IOException, NoSuchAlgorithmException {
        List<IndexEntry> entries = index.getEntries();
        // Bufor dynamiczny – zaczynamy od 1 MB, rośnie automatycznie
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // --- HEADER ---
        buffer.put("DIRC".getBytes());   // sygnatura
        buffer.putInt(index.getVersion());        // wersja indexu (2 lub 3)
        buffer.putInt(entries.size());   // liczba wpisów

        // --- ENTRIES ---
        index.sort();
        for (IndexEntry e : entries) {
            System.out.println("DEBUG - zapisuje entry: "+e.getPath());
            e.write(buffer);
        }

        // --- EXTENSIONS (opcjonalnie) ---
        // --- CHECKSUM ---
        byte[] checksum = index.getChecksum();
        if (checksum == null) {
            byte[] content = new byte[buffer.position()];
            buffer.rewind();
            buffer.get(content);
            try {
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                checksum = sha1.digest(content);
            } catch (NoSuchAlgorithmException e) {
                throw new NoSuchAlgorithmException("SHA-1 not found");
            }
        }
        // System.out.println("Checksum length(): "+checksum.length);
        buffer.put(checksum);

        // --- ZAPIS DO PLIKU ---
        buffer.flip();
        try (FileChannel ch = FileChannel.open(indexPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ch.write(buffer);
        } catch (IOException e) {
            throw new IOException("Couln't write to index file");
        }
    }

    public static GlitIndex parse(Path path) throws IOException {

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {

            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            buffer.flip(); // return to the beginning of the buffer

            // --- HEADER ---
            byte[] sig = new byte[4];
            buffer.get(sig);
            if (sig[0] != 'D' || sig[1] != 'I' || sig[2] != 'R' || sig[3] != 'C') {
                throw new IllegalStateException("Not a Glit index file: " + path);
            }

            int version = buffer.getInt();
            int entryCount = buffer.getInt();

            List<IndexEntry> entries = new ArrayList<>();

            // --- ENTRIES ---
            for (int i = 0; i < entryCount; i++) {
                entries.add(readEntry(buffer));
            }

            // // --- EXTENSIONS (pomijamy) ---
            // while (buffer.remaining() > 20) {
            //     byte[] extSig = new byte[4];
            //     buffer.get(extSig);
            //     int size = buffer.getInt();
            //     buffer.position(buffer.position() + size);
            // }
            // --- FINAL CHECKSUM ---
            byte[] checksum = new byte[20];

            buffer.get(checksum);

            return new GlitIndex(version, entries, checksum);
        }
    }

    private static IndexEntry readEntry(ByteBuffer buffer) {

        long ctimeSec = buffer.getInt() & 0xffffffffL;
        long ctimeNsec = buffer.getInt() & 0xffffffffL;
        long mtimeSec = buffer.getInt() & 0xffffffffL;
        long mtimeNsec = buffer.getInt() & 0xffffffffL;

        long dev = buffer.getInt() & 0xffffffffL;
        long ino = buffer.getInt() & 0xffffffffL;

        int mode = buffer.getInt();
        int uid = buffer.getInt();
        int gid = buffer.getInt();
        long fileSize = buffer.getInt() & 0xffffffffL;

        // TODO - check compatibility with HashUtils
        byte[] objectId = new byte[20];
        buffer.get(objectId);

        // int flags = buffer.getShort() & 0xffff;
        // --- PATH ---
        StringBuilder path = new StringBuilder();
        byte b;
        // null-terminated (0x00)
        while ((b = buffer.get()) != 0) {
            path.append((char) b);
        }

        // padding up to 8 bytes
        // 60 = 4*10 (ctime,mtime,dev,ino,mode,uid,gid,size) + 20 (hash)
        // 1 - null terminator from path
        int entryLength = 60 + path.length() + 1;
        int padding = (8 - (entryLength % 8)) % 8;

        buffer.position(buffer.position() + padding);

        return new IndexEntry(
                ctimeSec, ctimeNsec, mtimeSec, mtimeNsec,
                dev, ino, mode, uid, gid, fileSize,
                objectId, /*flags,*/ path.toString()
        );
    }
}
