package glit.model;
import glit.util.HashUtils;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    @Test
    public void testTreeDeserialization() throws IOException {
        // 1. test Data for entry (Entry)
        String mode = "100644";
        String fileName = "test.txt";
        String hexHash = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
        byte[] binaryHash = HashUtils.hexStringToByteArray(hexHash);


        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write((mode + " ").getBytes(StandardCharsets.UTF_8));
        baos.write(fileName.getBytes(StandardCharsets.UTF_8));
        baos.write(0);
        baos.write(binaryHash);

        byte[] treeContent = baos.toByteArray();

        Tree tree = new Tree(treeContent);

        assertNotNull(tree.getEntries(), "Lista entries nie powinna być nullem");
        assertEquals(1, tree.getEntries().size(), "Powinien być dokładnie jeden wpis");

        TreeEntry entry = tree.getEntries().get(0);
        assertEquals(mode, entry.mode(), "Mode powinien się zgadzać");
        assertEquals(fileName, entry.fileName(), "Nazwa pliku powinna się zgadzać");
        assertEquals(hexHash, entry.hash(), "Hash hex powinien zostać poprawnie odtworzony z binarnych bajtów");
    }
}
