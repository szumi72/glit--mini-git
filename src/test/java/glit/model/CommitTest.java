package glit.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class CommitTest {
    @Test
    public void commitHashChangesWithParent(){

        //Given
        Tree tree = new Tree();
        Commit parent = new Commit("Parent", tree.getHash(), "");

        //When
        Commit c1 = new Commit("m1",tree.getHash(),"");
        Commit c2 = new Commit("m1",tree.getHash(),parent.getHash());

        //Then
        assertNotEquals(c1.getHash(),c2.getHash());
    }

    @Test
    public void commitHashChangesWithContent(){
        //Given
        Tree tree = new Tree();

        //When
        Commit c1 = new Commit("m1",tree.getHash(),"");
        Commit c2 = new Commit("m2",tree.getHash(),"");

        //Then
        assertNotEquals(c1.getHash(),c2.getHash());
    }

    @Test
    public void testCommitDeserialization() {
        // Given:
        String treeHash = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
        String parentHash = "7890abcdef1234567890abcdef1234567890abcd";
        String author = "XYZ";
        long timestamp = 1715842800L;
        String timezone = "+0200";
        String message = "To jest testowa wiadomosc\nZ wieloma liniami.";

        String rawContent = "tree " + treeHash + "\n" +
                "parent " + parentHash + "\n" +
                "author " + author + " " + timestamp + " " + timezone + "\n" +
                "committer " + author + " " + timestamp + " " + timezone + "\n\n" +
                message;

        byte[] contentBytes = rawContent.getBytes(StandardCharsets.UTF_8);

        // When:
        Commit commit = new Commit(contentBytes);

        // Then:

        assertEquals(message, commit.getMessage(), "Wiadomość powinna być identyczna");
        assertEquals(treeHash, commit.getTreeHash(), "Hash drzewa powinien się zgadzać");
        assertEquals(parentHash, commit.getParentHash(), "Hash rodzica powinien się zgadzać");
        assertEquals(timestamp, commit.getTimestamp(), "Timestamp powinien być poprawnie odczytany");
        assertEquals(timezone, commit.getTimezone(), "Timezone powinien być poprawny");
    }
}
