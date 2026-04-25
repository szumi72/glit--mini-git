package glit.model;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import glit.util.HashUtils;
import java.nio.charset.StandardCharsets;

/**
 * Represents a Tree object in the Glit repository.
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

    ArrayList<TreeEntry> entries;

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
