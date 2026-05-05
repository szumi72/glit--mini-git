package glit.model;
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



}
