package glit.model;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class TreeTest {
    @Test
    public void sameHashForEmptyTree(){
        Tree b1 = new Tree();
        Tree b2 = new Tree();
        assertEquals(b1.getHash(),b2.getHash());
        assertNotEquals(b1,b2);
    }

    @Test
    public void HashChangeAfterTreeModification(){
        Tree t1 = new Tree();
        String emptyTreeHash = t1.getHash();
        t1.add(new Blob("test".getBytes()),"TestFile");
        String notEmptyTreeHash = t1.getHash();
        assertNotEquals(emptyTreeHash,notEmptyTreeHash);

    }
    @Test
    public void SameHashForDifferentOrder(){

        Tree t1 = new Tree();
        Tree t2 = new Tree();
        Blob b1 = new Blob(("test1").getBytes());
        Blob b2 = new Blob(("test2").getBytes());
        t1.add(b1,"TestFile1");
        t1.add(b2,"TestFile2");
        t2.add(b2,"TestFile2");
        t2.add(b1,"TestFile1");
        String t1Hash = t1.getHash();
        String t2Hash = t2.getHash();
        assertEquals(t1Hash,t2Hash);

    }
}
