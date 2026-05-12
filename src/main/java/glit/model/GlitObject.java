package glit.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Class GlitObject
 *
 * abstract base class for model classes like Blob, Tree and Commit.
 */
public abstract class GlitObject{
    /**
     * Unique hash evaluated by SHA-1 algorithm
     */
    protected String hash;

    //typ pliku(blob)(normalny = 100644, wykonywalny = 100755, link = 120000)
    /**
     * Type of file compatible withs UNIX specification
     */
    protected String mode="-";

    /**
     *
     * @return hash of the object
     */
    public String getHash(){return hash;}

    //zwraca typ danych(blob,tree,commit)
    public abstract String getType();

    public String getMode(){
        return mode;
    }

    @Override
    public String toString(){
        return getType()+ " " + hash;
    }

    protected byte[] prepareToHash(byte[] content){
        //dodawanie naglowka
        byte[] header = (getType()+ " " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(header.length + content.length).put(header).put(content).array();
    }

    public abstract void printContent();

    public abstract byte[] getContentWithHeader();
}