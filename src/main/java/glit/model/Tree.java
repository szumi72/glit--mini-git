package glit.model;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import glit.util.HashUtils;
import java.nio.charset.StandardCharsets;


public class Tree extends GlitObject {

    public Tree(){
        mode = "040000";
        entries = new ArrayList<TreeEntry>();
        updateHash();
    }

    ArrayList<TreeEntry> entries;

    //dodawanie elementow do drzewa
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
        try{
            ByteArrayOutputStream entriesBaos = new ByteArrayOutputStream();
            for(TreeEntry te:entries){
                entriesBaos.write((te.mode()+" ").getBytes(StandardCharsets.UTF_8));
                entriesBaos.write(te.fileName().getBytes(StandardCharsets.UTF_8));
                entriesBaos.write(0);
                entriesBaos.write(HashUtils.hexStringToByteArray(te.hash()));
            }

            byte[] treeEntriesByteStream = entriesBaos.toByteArray();
            ByteArrayOutputStream full = new ByteArrayOutputStream();
            String header = getType()+ " " + treeEntriesByteStream.length + "\0";
            full.write(header.getBytes(StandardCharsets.UTF_8));
            full.write(treeEntriesByteStream);

            byte[] toHash = full.toByteArray();
            hash = HashUtils.sha1(toHash);
        }catch (IOException e){
            System.err.println(e + "updateHash() -- Tree.java");
        }

    }
    //sortowanie drzewa
    private void sortTree(){
        entries.sort((t,x)->(t.fileName().compareTo(x.fileName())));
    }
}

record TreeEntry(String mode,String hash,String fileName){
    @Override
    public String toString(){
        return mode+ " " + hash + " " + fileName;
    }
}
