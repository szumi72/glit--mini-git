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

/**
 * Performs a three-way merge of tree objects (directories) in the Glit repository.
 * <p>
 * Given a base tree, "ours" tree, and "theirs" tree, this class determines the necessary
 * actions (take base, take ours, take theirs, or conflict) for each entry and recursively
 * merges subtrees. Blob conflicts are reported as {@link MergeConflictException}.
 * </p>
 * <p>
 * Usage:
 * <pre>
 *   TreeMerger merger = new TreeMerger(repoPath);
 *   String mergedHash = merger.mergeTree(baseHash, ourHash, theirHash);
 * </pre>
 * </p>
 */
public class TreeMerger {

    /** Path to the repository root (must contain a .glit directory). */
    private final Path repositoryPath;

    /** Object reader used to load base/ours/theirs trees and their contents. */
    private final ObjectReader reader;

    /** Object writer used to persist the resulting merged tree. */
    private final ObjectWriter writer;

    /**
     * Creates a new {@code TreeMerger} for the given repository.
     *
     * @param repositoryPath path to the repository root; must contain a valid {@code .glit} subdirectory
     * @throws MissingRepositoryException if {@code repositoryPath} is null or the {@code .glit} directory does not exist
     */
    public TreeMerger(Path repositoryPath) {
        if (repositoryPath == null || !Files.exists(repositoryPath.resolve(".glit"))) {
            throw new MissingRepositoryException();
        }
        this.repositoryPath = repositoryPath;
        this.reader = new ObjectReader(repositoryPath);
        this.writer = new ObjectWriter(repositoryPath);
    }

    /**
     * Returns the {@link ObjectReader} used by this merger.
     * @return the object reader
     */
    public ObjectReader getReader() {
        return reader;
    }

    /**
     * Returns the {@link ObjectWriter} used by this merger.
     * @return the object writer
     */
    public ObjectWriter getWriter() {
        return writer;
    }

    /**
     * Merges three versions of a tree (directory) and returns the hash of the resulting tree.
     * <p>
     * The merge algorithm follows Git's three‑way merge logic:
     * <ul>
     *   <li>If an entry is unchanged in both branches, the base entry is kept.</li>
     *   <li>If an entry is changed only in one branch, that branch's version is taken.</li>
     *   <li>If both branches made the same change, that change is taken.</li>
     *   <li>If both branches changed the same blob differently, a {@link MergeConflictException} is thrown.</li>
     *   <li>If both branches changed a subtree, the merge is performed recursively on that subtree.</li>
     *   <li>Structural conflicts (e.g. tree vs blob) also cause a {@link MergeConflictException}.</li>
     * </ul>
     * </p>
     *
     * @param baseTreeHash  hash of the base tree (may be {@code null} if no common ancestor)
     * @param oursTreeHash  hash of "our" tree (may be {@code null})
     * @param theirsTreeHash hash of "their" tree (may be {@code null})
     * @return the hash of the merged tree
     * @throws MergeConflictException if a merge conflict cannot be resolved automatically
     */
    public String mergeTree(String baseTreeHash, String oursTreeHash, String theirsTreeHash) {
        MergeAction rootAction = determineAction(baseTreeHash, oursTreeHash, theirsTreeHash);

        if (rootAction == MergeAction.TAKE_BASE) return baseTreeHash;
        if (rootAction == MergeAction.TAKE_OURS) return oursTreeHash;
        if (rootAction == MergeAction.TAKE_THEIRS) return theirsTreeHash;

        Tree baseTree = (baseTreeHash != null) ? (Tree) reader.readObject(baseTreeHash) : new Tree();
        Tree ourTree = (oursTreeHash != null) ? (Tree) reader.readObject(oursTreeHash) : new Tree();
        Tree theirTree = (theirsTreeHash != null) ? (Tree) reader.readObject(theirsTreeHash) : new Tree();

        Set<String> allEntriesNames = new HashSet<>();

        Map<String, TreeEntry> fromBaseMap = convertToMap(baseTree);
        Map<String, TreeEntry> fromOursMap = convertToMap(ourTree);
        Map<String, TreeEntry> fromTheirsMap = convertToMap(theirTree);

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

            MergeAction action = determineAction(hashBase, hashOurs, hashTheirs);

            switch (action) {
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
                    } else if ((isOursTree && !isTheirsTree) || (!isOursTree && isTheirsTree)) {
                        System.out.println("Structural MergeConflict: " + name);
                        throw new MergeConflictException();
                    } else if (!isBaseTree && !isOursTree && !isTheirsTree) {
                        // Pliki (bloby)
                        if (hashOurs == null && hashTheirs != null) {
                            // usunięcie w ours, modyfikacja w theirs – bierzemy theirs
                            if (fromTheirs != null) newTreeEntries.add(fromTheirs);
                        } else if (hashOurs != null && hashTheirs == null) {
                            // modyfikacja w ours, usunięcie w theirs – bierzemy ours
                            if (fromOurs != null) newTreeEntries.add(fromOurs);
                        } else if (oursTreeHash != null && theirsTreeHash != null) {
                            // oba blob istnieją – próba scalenia
                            FileMerger merger = new FileMerger(reader, writer);
                            String mergedBlobHash = merger.mergeFiles(hashBase, hashOurs, hashTheirs);
                            TreeEntry mergedEntry = new TreeEntry("100644", mergedBlobHash, name);
                            newTreeEntries.add(mergedEntry);
                        }
                    }
                }
            }
        }

        Tree mainTree = new Tree(newTreeEntries);
        String mainHash = writer.saveObject(mainTree);
        return mainHash;
    }

    /**
     * Converts a tree's entries into a map keyed by file/directory name.
     *
     * @param tree the tree to convert (may be null)
     * @return a map from entry name to {@link TreeEntry}; empty if tree is null or has no entries
     */
    private Map<String, TreeEntry> convertToMap(Tree tree) {
        Map<String, TreeEntry> map = new HashMap<>();
        if (tree == null || tree.getEntries() == null) return map;
        for (TreeEntry entry : tree.getEntries()) {
            map.put(entry.fileName(), entry);
        }
        return map;
    }

    /**
     * Enumeration of possible merge decisions for an entry.
     */
    private enum MergeAction {
        /** Keep the base version (entry unchanged in both branches). */
        TAKE_BASE,
        /** Take the "our" version (only we changed it, or both made the same change). */
        TAKE_OURS,
        /** Take the "their" version (only they changed it). */
        TAKE_THEIRS,
        /** Conflict – further inspection needed (tree vs tree, tree vs blob, or blob vs blob). */
        CONFLICT
    }

    /**
     * Determines which merge action to take based on the three hashes.
     *
     * @param hashBase   hash of the base entry (or null)
     * @param hashOurs   hash of our entry (or null)
     * @param hashTheirs hash of their entry (or null)
     * @return the appropriate {@link MergeAction}
     */
    private MergeAction determineAction(String hashBase, String hashOurs, String hashTheirs) {
        boolean isOurChanged = !Objects.equals(hashBase, hashOurs);
        boolean isTheirChanged = !Objects.equals(hashBase, hashTheirs);
        boolean sameOursTheirs = Objects.equals(hashOurs, hashTheirs);

        if (!isOurChanged && !isTheirChanged) {
            return MergeAction.TAKE_BASE;
        } else if (isOurChanged && !isTheirChanged) {
            return MergeAction.TAKE_OURS;
        } else if (!isOurChanged && isTheirChanged) {
            return MergeAction.TAKE_THEIRS;
        } else if (sameOursTheirs) {
            return MergeAction.TAKE_OURS;
        }
        return MergeAction.CONFLICT;
    }

    /**
     * Checks whether an entry represents a subtree (mode 040000).
     *
     * @param entry the tree entry (may be null)
     * @return {@code true} if the entry is not null and its mode equals {@code "040000"}
     */
    private boolean isTree(TreeEntry entry) {
        return entry != null && "040000".equals(entry.mode());
    }

    /**
     * Checks whether an entry represents a regular file (blob, mode 100644).
     *
     * @param entry the tree entry (may be null)
     * @return {@code true} if the entry is not null and its mode equals {@code "100644"}
     */
    private boolean isBlob(TreeEntry entry) {
        return entry != null && "100644".equals(entry.mode());
    }
}