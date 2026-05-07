package glit.model;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class TreeTest {
    @Test
    public void sameHashForEmptyTree(){
        //Given
        Tree b1 = new Tree();
        Tree b2 = new Tree();

        //Than
        assertEquals(b1.getHash(),b2.getHash());
        assertNotEquals(b1,b2);
    }

    @Test
    public void HashChangeAfterTreeModification(){
        //Given
        Tree t1 = new Tree();
        String emptyTreeHash = t1.getHash();

        //When
        t1.add(new Blob("test".getBytes()),"TestFile");
        String notEmptyTreeHash = t1.getHash();

        //Then
        assertNotEquals(emptyTreeHash,notEmptyTreeHash);

    }
    @Test
    public void SameHashForDifferentOrder(){
        //Given
        Tree t1 = new Tree();
        Tree t2 = new Tree();
        Blob b1 = new Blob(("test1").getBytes());
        Blob b2 = new Blob(("test2").getBytes());

        //When
        t1.add(b1,"TestFile1");
        t1.add(b2,"TestFile2");
        t2.add(b2,"TestFile2");
        t2.add(b1,"TestFile1");
        String t1Hash = t1.getHash();
        String t2Hash = t2.getHash();

        //Then
        assertEquals(t1Hash,t2Hash);

    }
}
