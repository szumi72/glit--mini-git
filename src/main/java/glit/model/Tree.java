package glit.model;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import glit.util.HashUtils;
import glit.service.Repository;
import glit.storage.ObjectWriter;

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

    public Tree(List<TreeEntry> entries){
        mode = "040000";
        this.entries = new ArrayList<>(entries);
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

    private static class TreeNode {
        String name;
        boolean directory;
        Map<String, TreeNode> children; // tylko dla katalogów
        String hash; // opcjonalnie: jak w Git

        TreeNode(String name, boolean directory) {
            this.name = name;
            this.directory = directory;
            this.children = directory ? new TreeMap<>() : null;
        }
    }

    private static void addPath(TreeNode root, String path) {
        String[] parts = path.split("/");

        TreeNode current = root;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isDir = (i < parts.length - 1);

            current.children.putIfAbsent(part, new TreeNode(part, isDir));
            current = current.children.get(part);
        }
    }

    private static TreeNode buildTreeNodeMap(Map<String, String> indexMap){
        TreeNode root = new TreeNode("", true);

        for (String p : indexMap.keySet()) {
            addPath(root, p);
        }
        return root;
    }

    private static Tree buildTree(TreeNode node, Map<String, String> indexMap, String currentPath) {
        List<TreeEntry> entries = new ArrayList<>();

        for (TreeNode child : node.children.values()) {
            String childPath = currentPath.isEmpty()
                    ? child.name
                    : currentPath + "/" + child.name;

            if (child.directory) {
                // Rekurencyjnie budujemy pod-tree
                Tree subTree = buildTree(child, indexMap, childPath);

                // Hash katalogu to hash jego Tree
                subTree.updateHash();
                String treeHash = subTree.getHash();

                entries.add(new TreeEntry("040000", treeHash, child.name));
            } else {
                // Plik — hash z indexMap
                String blobHash = indexMap.get(childPath);

                entries.add(new TreeEntry("100644", blobHash, child.name));
            }
        }

        Tree t = new Tree(entries);
        ObjectWriter writer = new ObjectWriter(Repository.REPOSITORY_PATH);
        writer.saveObject(t);
        return t;
    }

    /**
     * creates and writes Tree from directory structure
     * @param indexMap - map [full_path -> hash]
     */
    public static Tree createAndWriteTree(Map<String,String> indexMap){
        return buildTree(buildTreeNodeMap(indexMap), indexMap, "");
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

    @Override
    public void printContent(){
       for(TreeEntry te:entries){
           System.out.println(te);
       }
    }

}


