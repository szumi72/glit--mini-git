package glit.model;
import glit.util.HashUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Commit extends GlitObject{
    public Commit(String message,GlitObject o,Commit parent){
        this.parent = parent;
        this.data = o;
        this.message = message;
        ZonedDateTime now = ZonedDateTime.now();
        timestamp = now.toEpochSecond();
        timezone = now.format(DateTimeFormatter.ofPattern("xx"));
        setHash();
    }
    //TODO
    //to trzeba bedzie ustawic zeby sie gdzies zapisywało i czytało z jakieg pliku
    final private String author = "XYZ";
    final private long timestamp;
    final String timezone;
    final private String message;
    final private GlitObject data;
    final private Commit parent;

    @Override
    public String getType() {
        return "commit";
    }

    private String buildCommitContent(){
        String content = data + "\n";
        if(parent != null){
            content += "parent " + parent.getHash() + "\n";
        }
        content += "author " + author + " " + timestamp +" "+ timezone +"\n" + "committer " + author +" "+ timestamp +" "+ timezone + "\n\n" + message;
        return content;
    }

    private void setHash(){
        byte[] content = buildCommitContent().getBytes(StandardCharsets.UTF_8);
        String header = "commit " + content.length + "\0";
        try{
            ByteArrayOutputStream finalCommitStream = new ByteArrayOutputStream();
            finalCommitStream.write(header.getBytes(StandardCharsets.UTF_8));
            finalCommitStream.write(content);
            byte[] toHash = finalCommitStream.toByteArray();
            this.hash = HashUtils.sha1(toHash);
        }catch (IOException e){
           System.err.println(e + "setHash() -- Commit.java");
        }
    }

    public static void main(String[] args){
        Commit c = new Commit("testowy commit",new Tree(),null);
        System.out.println(c.buildCommitContent());
    }
}
