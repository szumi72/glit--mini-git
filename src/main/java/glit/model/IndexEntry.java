package glit.model;

import java.nio.ByteBuffer;

public class IndexEntry {

    private final long ctimeSec;
    private final long ctimeNsec;
    private final long mtimeSec;
    private final long mtimeNsec;
    private final long dev;
    private final long ino;
    private final int mode;
    private final int uid;
    private final int gid;
    private final long fileSize;
    private final byte[] objectId;
    // private final int flags;
    private final String path;

    public IndexEntry(long ctimeSec, long ctimeNsec, long mtimeSec, long mtimeNsec,
            long dev, long ino, int mode, int uid, int gid, long fileSize,
            byte[] objectId, /*int flags,*/ String path) {
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
        // this.flags = flags;
        this.path = path;
    }

    public void write(ByteBuffer buffer) {
        // --- PODSTAWOWE POLA ---
        buffer.putInt((int) ctimeSec);
        buffer.putInt((int) ctimeNsec);
        buffer.putInt((int) mtimeSec);
        buffer.putInt((int) mtimeNsec);

        buffer.putInt((int) dev);
        buffer.putInt((int) ino);

        buffer.putInt(mode);
        buffer.putInt(uid);
        buffer.putInt(gid);
        buffer.putInt((int) fileSize);

        // --- HASH BLOBA ---
        buffer.put(objectId);

        // // --- FLAGS ---
        // buffer.putShort((short) flags);

        // --- PATH ---
        byte[] pathBytes = path.getBytes();
        buffer.put(pathBytes);
        buffer.put((byte) 0); // null terminator

        // padding up to 8 bytes
        // 60 = 4*10 (ctime,mtime,dev,ino,mode,uid,gid,size) + 20 (hash)
        // 1 - null terminator from path
        int entryLength = 60 + pathBytes.length + 1;
        int padding = (8 - (entryLength % 8)) % 8;

        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0);
        }
    }
}
