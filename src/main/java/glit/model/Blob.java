package glit.model;
import java.nio.charset.StandardCharsets;
import glit.util.HashUtils;
import java.nio.ByteBuffer;

public class Blob extends GlitObject{

    //sam content przy hashu trzeba dokleic nagłówek
    public Blob(byte[] content){
        mode="100644";
        this.content = content;

        //przygotowanie do hashowania(trzeba dodać header do hasha)
        byte[] header = ("blob " + content.length + "\0").getBytes();
        byte[] toHash = ByteBuffer.allocate(header.length + content.length).put(header).put(content).array();
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

    public static void main(String[] args){
        GlitObject b = new Blob("Ala".getBytes(StandardCharsets.UTF_8));
        System.out.println("b="+b);
        System.out.println("TEST");
    }
}
