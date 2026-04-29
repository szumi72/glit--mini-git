package glit.model;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;


public class BlobTest {
    @Test
    public void sameHashForSameContent(){
        //Given
        Blob b1 = new Blob(("test").getBytes());
        Blob b2 = new Blob(("test").getBytes());

        //When:
        assertEquals(b1.getHash(),b2.getHash());

        //Then:
        assertNotEquals(b1,b2);
    }


}
