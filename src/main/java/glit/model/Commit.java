package glit.model;
import glit.util.HashUtils;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Class Commit
 */
public class Commit extends GlitObject{
    /**
     * Commit constructor
     * @param message - commit message
     * @param o - blob/tree that is added to commit
     * @param parent - previous parent commit (if first commit parent = null)
     */
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
        byte[] toHash = prepareToHash(content);
        this.hash = HashUtils.sha1(toHash);
    }

    public byte[] getContentWithHeader(){
        byte[] content = buildCommitContent().getBytes(StandardCharsets.UTF_8);
        return prepareToHash(content);
    }

}
