package glit.model;
import glit.util.HashUtils;


public class Blob extends GlitObject{

    //sam content przy hashu trzeba dokleic nagłówek
    public Blob(byte[] content){
        mode="100644";
        this.content = content;

        //przygotowanie do hashowania(trzeba dodać header do hasha)
        byte[] toHash = prepareToHash(content);
        this.hash = HashUtils.sha1(toHash);
    }
    //zawartosc pliku
    private final byte[] content;

    public byte[] getContent() {
        return content;
    }
    @Override
    public String getType(){
        return "blob";
    }



}
