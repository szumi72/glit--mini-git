package glit.model;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import glit.util.HashUtils;
import java.nio.charset.StandardCharsets;

/**
 * Represents ObjectReader.java Tree object in the Glit repository.
 * A tree stores information about files and subdirectories.
 */
public class Tree extends GlitObject {
    /**
     * Initializes an empty tree with the standard directory mode.
     */
    public Tree(){
        mode = "040000";
        entries = new ArrayList<TreeEntry>();
        updateHash();
    }

    public Tree(byte [] content){
        mode = "040000";
        entries = new ArrayList<TreeEntry>();

        ByteBuffer buffer = ByteBuffer.wrap(content);
        String entryMode;
        String entryFileName;
        String entryHash;

        while(buffer.hasRemaining()){
            StringBuilder modeBuilder = new StringBuilder();
            byte b;
            while((b=buffer.get()) != ' '){
                modeBuilder.append((char)b);
            }
            entryMode = modeBuilder.toString();

            StringBuilder fileNameBuilder = new StringBuilder();
            while((b=buffer.get()) != 0){
                fileNameBuilder.append((char)b);
            }
            entryFileName = fileNameBuilder.toString();

            byte[] hashBytes = new byte[20];
            buffer.get(hashBytes);
            entryHash = HashUtils.byteArrayToHexString(hashBytes);
            entries.add(new TreeEntry(entryMode,entryHash,entryFileName));
        }

        updateHash();
    }

    private ArrayList<TreeEntry> entries;

    public ArrayList<TreeEntry> getEntries(){return entries;}
    //dodawanie elementow do drzewa

    /**
     * Adds an entry to the tree and recalculates its hash.
     * @param o The object to be added (Blob or another Tree).
     * @param name The name of the file or directory.
     */
    public void add(GlitObject o,String name){
        entries.add(new TreeEntry(o.getMode(),o.getHash(),name));
        updateHash();
    }
    @Override
    public String getType() {
        return "tree";
    }

    //update/ustawienie hasha na podstawie całej zawartości
    private void updateHash(){
        sortTree();
        byte[] toHash = prepareToHash(produceContentFromEntries());
        hash = HashUtils.sha1(toHash);
    }
    //sortowanie drzewa
    private void sortTree(){
        entries.sort((t,x)->(t.fileName().compareTo(x.fileName())));
    }

    //zrobienie contentu z całej listy entries
    private byte[] produceContentFromEntries(){
        try{
            ByteArrayOutputStream entriesBaos = new ByteArrayOutputStream();
            for(TreeEntry te:entries){
                entriesBaos.write((te.mode()+" ").getBytes(StandardCharsets.UTF_8));
                entriesBaos.write(te.fileName().getBytes(StandardCharsets.UTF_8));
                entriesBaos.write(0);
                entriesBaos.write(HashUtils.hexStringToByteArray(te.hash()));
            }
            return entriesBaos.toByteArray();
        }catch (IOException e){
            throw new RuntimeException(e + "tree content failed");
        }
    }
    @Override
    public byte[] getContentWithHeader(){
        return prepareToHash(produceContentFromEntries());
    }

}

/**
 * TreeEntry record
 * @param mode Mode of the file(regular = 100644,dictionary = 040000)
 * @param hash Hash of the added object
 * @param fileName Name of the file
 */
record TreeEntry(String mode,String hash,String fileName){
    @Override
    public String toString(){
        return mode+ " " + hash + " " + fileName;
    }
}
