package glit.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

import glit.storage.ObjectWriter;
import glit.util.HashUtils;


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

    public IndexEntry(Path path, byte[] hash) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        this.ctimeSec = attrs.creationTime().to(TimeUnit.SECONDS);
        this.ctimeNsec = attrs.creationTime().to(TimeUnit.NANOSECONDS);
        this.mtimeSec = attrs.lastModifiedTime().to(TimeUnit.SECONDS);
        this.mtimeNsec = attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS);
        this.dev = (long) Files.getAttribute(path, "unix:dev");
        this.ino = (long) Files.getAttribute(path, "unix:ino");
        this.mode = (int) Files.getAttribute(path, "unix:mode");
        this.uid = (int) Files.getAttribute(path, "unix:uid");
        this.gid = (int) Files.getAttribute(path, "unix:gid");
        this.fileSize = 0;
        this.objectId = hash;
        this.path = path.toString();
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

    public long getCtimeSec() {
        return ctimeSec;
    }

    public long getCtimeNsec() {
        return ctimeNsec;
    }

    public long getMtimeSec() {
        return mtimeSec;
    }

    public long getMtimeNsec() {
        return mtimeNsec;
    }

    public long getDev() {
        return dev;
    }

    public long getIno() {
        return ino;
    }

    public int getMode() {
        return mode;
    }

    public int getUid() {
        return uid;
    }

    public int getGid() {
        return gid;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getObjectId() {
        return objectId;
    }

    public String getPath() {
        return path;
    }

    public static IndexEntry createFromPath(Path path, Path repositoryPath) throws IOException {
        IndexEntry entry = null;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.read(buffer);
            GlitObject o = new Blob(buffer.array());
            ObjectWriter writer = new ObjectWriter(repositoryPath);
            String hash = writer.saveObject(o);
            entry = new IndexEntry(path, HashUtils.hexStringToByteArray(hash));
        }catch(IOException e){throw new IOException("Couldn't create Blob from "+path);}
        return entry;
    }

}
