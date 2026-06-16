package glit.storage;

import glit.exceptions.GlitException;
import glit.exceptions.MissingRepositoryException;
import glit.model.GlitObject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;

/**
 * <p>Component responsible for serializing and storing {@link GlitObject} instances
 * into the local repository's object database.</p>
 *
 * <p>Similar to Git, it saves objects using Zlib compression and organizes them
 * in the {@code .glit/objects} directory. The first two characters of the object's
 * hash form the subdirectory name, while the remaining characters form the file name.</p>
 */
public class ObjectWriter {

    private final Path repositoryPath;

    /**
     * <p>Constructs an {@code ObjectWriter} tied to a specific repository.</p>
     *
     * @param repositoryPath the absolute path to the root of the repository
     * @throws MissingRepositoryException if the path is {@code null} or does not contain a valid {@code .glit} directory
     */
    public ObjectWriter(Path repositoryPath){
        if(repositoryPath == null || !Files.exists(repositoryPath.resolve(".glit"))){
            throw new MissingRepositoryException();
        }
        this.repositoryPath = repositoryPath;
    }

    /**
     * <p>Saves a fully constructed {@link GlitObject} to the physical storage.</p>
     *
     * <p>This method automatically extracts the computed hash and
     * the binary payload (including the header) from the object for storage.</p>
     *
     * @param o the object (Blob, Tree, or Commit) to be saved
     * @return a {@link String} representing the hash of the saved object
     */
    public String saveObject(GlitObject o){
        save(o.getHash(),o.getContentWithHeader());
        return o.getHash();
    }

    /**
     * <p>Handles the low-level file system operations required to persist an object.</p>
     *
     * <p>This method performs the following steps:</p>
     * <ul>
     * <li>Splits the hash to determine the target directory (first 2 chars) and file name (remaining chars).</li>
     * <li>Creates the necessary subdirectory inside {@code .glit/objects/} if it does not exist.</li>
     * <li>Silently returns if the object file already exists, ensuring idempotency and saving I/O.</li>
     * <li>Compresses the raw byte array using {@link DeflaterOutputStream} and writes it to disk.</li>
     * </ul>
     *
     * @param hash              the unique hash representing the object
     * @param contentWithHeader the raw byte array containing the object's formatted header and content
     * @throws GlitException if a filesystem error occurs during directory creation or stream writing
     */
    private void save(String hash,byte[] contentWithHeader){

        String dirName = hash.substring(0,2);
        String fileName = hash.substring(2);

        Path dirPath = repositoryPath.resolve(".glit/objects/").resolve(dirName);
        Path filePath = dirPath.resolve(fileName);
        try{
            Files.createDirectories(dirPath);
            if(Files.exists(filePath)){
                return;
            }
            try(DeflaterOutputStream dos = new DeflaterOutputStream(Files.newOutputStream(filePath));
                BufferedOutputStream bos = new BufferedOutputStream(dos) )
            {
                bos.write(contentWithHeader);
            }

        } catch (IOException e) {
            throw new GlitException("fatal: Cannot save object to storage", e);
        }

    }
}