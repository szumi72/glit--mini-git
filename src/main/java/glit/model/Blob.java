package glit.model;
import java.nio.charset.StandardCharsets;

import glit.util.HashUtils;

/**
 * Represents ObjectReader.java Blob object in the Glit repository.
 *
 */
public class Blob extends GlitObject{

    //sam content przy hashu trzeba dokleic nagłówek

    /**
     * Initialize Blob from provided content.
     * @param content File content.
     */
    public Blob(byte[] content){
        mode="100644";
        this.content = content;
        //przygotowanie do hashowania(trzeba dodać header do hasha)
        byte[] toHash = prepareToHash(content);
        this.hash = HashUtils.sha1(toHash);

        // System.out.println("Dlugosc hasha: " + hash.length());
    }
    //zawartosc pliku
    private final byte[] content;

    /**
     *
     * @return content of the Blob
     */
    public byte[] getContent() {
        return content;
    }
    @Override
    public String getType(){
        return "blob";
    }
    @Override
    public byte[] getContentWithHeader(){
        return prepareToHash(content);
    }
    @Override
    public void printContent(){
        String contentString = new String(this.content, StandardCharsets.UTF_8);
        System.out.println(contentString);
    }


}
