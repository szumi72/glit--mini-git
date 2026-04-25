package glit.util;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class HashTest {
    @Test
    public void sha1Test(){
        String expectedHash = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3";
        String testHash = HashUtils.sha1("test".getBytes());
        assertEquals(expectedHash,testHash);
    }
}
