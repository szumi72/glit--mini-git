package glit.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class CommitTest {
    @Test
    public void commitHashChangesWithParent(){
        Tree tree = new Tree();
        Commit parent = new Commit("Parent", tree, null);
        Commit c1 = new Commit("m1",tree,null);
        Commit c2 = new Commit("m1",tree,parent);
        assertNotEquals(c1.getHash(),c2.getHash());
    }

    @Test
    public void commitHashChangesWithContent(){
        Tree tree = new Tree();
        Commit c1 = new Commit("m1",tree,null);
        Commit c2 = new Commit("m2",tree,null);
        assertNotEquals(c1.getHash(),c2.getHash());
    }
}
