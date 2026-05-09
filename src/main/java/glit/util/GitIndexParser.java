package glit.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class GitIndexParser {

    public static class IndexEntry {
        public final long ctimeSec;
        public final long ctimeNsec;
        public final long mtimeSec;
        public final long mtimeNsec;
        public final long dev;
        public final long ino;
        public final int mode;
        public final int uid;
        public final int gid;
        public final long fileSize;
        public final byte[] objectId;
        public final int flags;
        public final String path;

        public IndexEntry(long ctimeSec, long ctimeNsec, long mtimeSec, long mtimeNsec,
                          long dev, long ino, int mode, int uid, int gid, long fileSize,
                          byte[] objectId, int flags, String path) {
            this.ctimeSec = ctimeSec;
            this.ctimeNsec = ctimeNsec;
            this.mtimeSec = mtimeSec;
            this.mtimeNsec = mtimeNsec;
            this.dev = dev;
            this.ino = ino;
            this.mode = mode;
            this.uid = uid;
            this.gid = gid;
            this.fileSize = fileSize;
            this.objectId = objectId;
            this.flags = flags;
            this.path = path;
        }
    }

    public static class GitIndex {
        public final int version;
        public final List<IndexEntry> entries;
        public final byte[] checksum;

        public GitIndex(int version, List<IndexEntry> entries, byte[] checksum) {
            this.version = version;
            this.entries = entries;
            this.checksum = checksum;
        }
    }

    public static GitIndex parse(Path path) throws IOException {

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {

            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            buffer.flip();

            // --- HEADER ---
            byte[] sig = new byte[4];
            buffer.get(sig);
            String signature = new String(sig);

            if (!signature.equals("DIRC")) {
                throw new IllegalStateException("Not a Git index file");
            }

            int version = buffer.getInt();
            int entryCount = buffer.getInt();

            List<IndexEntry> entries = new ArrayList<>();

            // --- ENTRIES ---
            for (int i = 0; i < entryCount; i++) {
                entries.add(readEntry(buffer));
            }

            // --- EXTENSIONS (pomijamy) ---
            while (buffer.remaining() > 20) {
                byte[] extSig = new byte[4];
                buffer.get(extSig);
                int size = buffer.getInt();
                buffer.position(buffer.position() + size);
            }

            // --- FINAL CHECKSUM ---
            byte[] checksum = new byte[20];
            buffer.get(checksum);

            return new GitIndex(version, entries, checksum);
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

        byte[] objectId = new byte[20];
        buffer.get(objectId);

        int flags = buffer.getShort() & 0xffff;

        // --- PATH (null-terminated UTF-8) ---
        StringBuilder path = new StringBuilder();
        byte b;
        while ((b = buffer.get()) != 0) {
            path.append((char) b);
        }

        // --- PADDING DO 8 BAJTÓW ---
        int entryLength = 62 + path.length() + 1;
        int padding = (8 - (entryLength % 8)) % 8;
        buffer.position(buffer.position() + padding);

        return new IndexEntry(
                ctimeSec, ctimeNsec, mtimeSec, mtimeNsec,
                dev, ino, mode, uid, gid, fileSize,
                objectId, flags, path.toString()
        );
    }
}
