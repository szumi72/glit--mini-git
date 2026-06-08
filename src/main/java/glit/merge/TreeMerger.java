package glit.merge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import glit.exceptions.MergeConflictException;
import glit.exceptions.MissingRepositoryException;
import glit.model.Tree;
import glit.model.TreeEntry;
import glit.storage.ObjectReader;
import glit.storage.ObjectWriter;


public class TreeMerger{
    
    private final Path repositoryPath;
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public TreeMerger(Path repositoryPath){
        
        if(repositoryPath == null || !Files.exists(repositoryPath.resolve(".glit"))){
            throw new MissingRepositoryException();
        }
        this.repositoryPath = repositoryPath;
        this.reader = new ObjectReader(repositoryPath);
        this.writer = new ObjectWriter(repositoryPath);
    }

    public ObjectReader getReader(){
        return reader;
    }

    public ObjectWriter getWriter(){
        return writer;
    }

    public String mergeTree(String baseTreeHash,String oursTreeHash,String theirsTreeHash){

        MergeAction rootAction = determineAction(baseTreeHash, oursTreeHash, theirsTreeHash);
    
        if (rootAction == MergeAction.TAKE_BASE) return baseTreeHash;
        if (rootAction == MergeAction.TAKE_OURS) return oursTreeHash;
        if (rootAction == MergeAction.TAKE_THEIRS) return theirsTreeHash;

        Tree baseTree = (baseTreeHash != null) ? (Tree)reader.readObject(baseTreeHash) : new Tree();
        Tree ourTree = (oursTreeHash != null) ? (Tree)reader.readObject(oursTreeHash) : new Tree();
        Tree theirTree = (theirsTreeHash != null) ? (Tree)reader.readObject(theirsTreeHash) : new Tree();

        Set<String> allEntriesNames = new HashSet<>();
        
        Map<String,TreeEntry> fromBaseMap = convertToMap(baseTree);
        Map<String,TreeEntry> fromOursMap = convertToMap(ourTree);
        Map<String,TreeEntry> fromTheirsMap = convertToMap(theirTree);

        allEntriesNames.addAll(fromBaseMap.keySet());
        allEntriesNames.addAll(fromOursMap.keySet());
        allEntriesNames.addAll(fromTheirsMap.keySet());

        ArrayList<TreeEntry> newTreeEntries = new ArrayList<>();

        for (String name : allEntriesNames) {
            TreeEntry fromBase = fromBaseMap.get(name);
            TreeEntry fromOurs = fromOursMap.get(name);
            TreeEntry fromTheirs = fromTheirsMap.get(name);

            
            String hashBase = (fromBase != null) ? fromBase.hash() : null;
            String hashOurs = (fromOurs != null) ? fromOurs.hash() : null;
            String hashTheirs = (fromTheirs != null) ? fromTheirs.hash() : null;

            //tutaj bedzie porównywanie przypadków i jak potrzeba tworzenie nowego drzewa z jako ten katalog główny
            MergeAction action = determineAction(hashBase, hashOurs, hashTheirs);

            switch(action){
                case TAKE_BASE -> { if (fromBase != null) newTreeEntries.add(fromBase); }
                case TAKE_OURS -> { if (fromOurs != null) newTreeEntries.add(fromOurs); }
                case TAKE_THEIRS -> { if (fromTheirs != null) newTreeEntries.add(fromTheirs); }
                case CONFLICT -> {
                    boolean isBaseTree = isTree(fromBase);
                    boolean isOursTree = isTree(fromOurs);
                    boolean isTheirsTree = isTree(fromTheirs);
                    
                    if (isBaseTree && isOursTree && isTheirsTree) {
                        String mergedTreeHash = mergeTree(hashBase, hashOurs, hashTheirs); 
                        TreeEntry mergedEntry = new TreeEntry("040000", mergedTreeHash, name);
                        newTreeEntries.add(mergedEntry);   
                    }else if ((isOursTree && !isTheirsTree)||(!isOursTree && isTheirsTree)){
                        System.out.println("Structural MergeConflict: " + name);
                        throw new MergeConflictException();
                    } else if(!isBaseTree && !isOursTree && !isTheirsTree && (oursTreeHash!=null && theirsTreeHash!=null)){
                        //konflikt blobów trzeba dopisać BlobMerger
                        System.out.println("MergeConflict in file: " + name);
                        throw new MergeConflictException();
                    }                    
                   
                }
            }
            


        }

        Tree mainTree = new Tree(newTreeEntries);
        String mainHash = writer.saveObject(mainTree);
        
        return mainHash;
    }
   
    private Map<String, TreeEntry> convertToMap(Tree tree) {
        Map<String, TreeEntry> map = new HashMap<>();
        if (tree == null || tree.getEntries() == null) return map;
        
        for (TreeEntry entry : tree.getEntries()) {
            map.put(entry.fileName(), entry);
        }
        return map;
    }

    private enum MergeAction{
        TAKE_BASE,
        TAKE_OURS,
        TAKE_THEIRS,
        CONFLICT        
    }

    private MergeAction determineAction(String hashBase, String hashOurs, String hashTheirs){
        boolean isOurChanged = !Objects.equals(hashBase,hashOurs);
        boolean isTheirChanged = !Objects.equals(hashBase, hashTheirs);
        boolean sameOursTheirs = Objects.equals(hashOurs, hashTheirs);


        if(!isOurChanged && !isTheirChanged){
            return MergeAction.TAKE_BASE;
        }else if(isOurChanged && !isTheirChanged){
            return MergeAction.TAKE_OURS;
        }else if(!isOurChanged && isTheirChanged){
            return MergeAction.TAKE_THEIRS;
        }else if(sameOursTheirs){
            return MergeAction.TAKE_OURS;
        }
        return MergeAction.CONFLICT;
    }

    private boolean isTree(TreeEntry entry) {
        return entry != null && "040000".equals(entry.mode());
    }

    private boolean isBlob(TreeEntry entry) {
        return entry != null && "100644".equals(entry.mode());
    }
}


