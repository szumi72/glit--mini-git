package glit.model;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import glit.util.HashUtils;

/**
 * Class Commit
 */
public class Commit extends GlitObject{
    /**
     * Commit constructor
     * @param message - commit message
     * @param objectHash - tree Hash that is added to commit
     * @param parentHash - previous parent commit hash (if first commit parent = null)
     */
    public Commit(String message,String objectHash,String parentHash){
        this.parentHash = parentHash;
        this.treeHash = objectHash;
        this.message = message;
        ZonedDateTime now = ZonedDateTime.now();
        timestamp = now.toEpochSecond();
        timezone = now.format(DateTimeFormatter.ofPattern("xx"));
        setHash();
    }

    public Commit(byte [] content){
        String fullContent = new String(content,StandardCharsets.UTF_8);

        String[] sections = fullContent.split(System.lineSeparator()+System.lineSeparator(), 2);
        String headersPart = sections[0];
        this.message = sections.length > 1 ? sections[1] : "";

        String [] lines = headersPart.split(System.lineSeparator());

        String tHash = null;
        String pHash = null;
        String auth = "XYZ";
        long time = 0;
        String zone = "";

        for(String line :lines){
            if(line.startsWith("tree ")){
                tHash = line.substring(5);
            } else if (line.startsWith("parent ")) {
                pHash = line.substring(7);
            } else if (line.startsWith("author ")) {
                String [] parts = line.split(" ");
                auth = parts[1];
                time = Long.parseLong(parts[2]);
                zone = parts[3];
            }

        }
        this.treeHash = tHash;
        this.parentHash = pHash;
        this.author = auth;
        this.timestamp = time;
        this.timezone = zone;
        setHash();
    }

    //TODO
    //to trzeba bedzie ustawic zeby sie gdzies zapisywało i czytało z jakieg pliku
    private String author = "The best programmist ever";
    final private long timestamp;
    final private String timezone;
    final private String message;
    final private String treeHash;
    final private String parentHash;


    @Override
    public String getType() {
        return "commit";
    }

    private String buildCommitContent(){
        StringBuilder sb = new StringBuilder();
        sb.append("tree ").append(treeHash).append(System.lineSeparator());
        if (parentHash != null) {
            sb.append("parent ").append(parentHash).append(System.lineSeparator());
        }
        sb.append("author ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append(System.lineSeparator());
        sb.append("committer ").append(author).append(" ").append(timestamp).append(" ").append(timezone).append(System.lineSeparator()).append(System.lineSeparator());
        sb.append(message);
        return sb.toString();
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
    @Override
    public void printContent(){
        String contentString = buildCommitContent();
        System.out.println(contentString);
    }

    public String getMessage(){return message;}
    public String getAuthor(){return author;}
    public String getTimezone(){return timezone;}
    public String getTreeHash(){return treeHash;}
    public String getParentHash(){return parentHash;}
    public long getTimestamp(){return timestamp;}
}
