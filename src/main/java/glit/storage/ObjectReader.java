package glit.storage;

import glit.exceptions.GlitException;
import glit.model.Blob;
import glit.model.Commit;
import glit.model.GlitObject;
import glit.model.Tree;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import glit.exceptions.MissingRepositoryException;
import java.util.zip.InflaterInputStream;

/**
 * <p>Component responsible for reading, decompressing, and deserializing {@link GlitObject}
 * instances from the local repository's object database.</p>
 *
 * <p>It reconstructs the appropriate domain objects (Blobs, Trees, or Commits) by
 * parsing their metadata headers stored within the compressed files.</p>
 */
public class ObjectReader {

    private final Path repositoryPath;

    /**
     * <p>Constructs an {@code ObjectReader} tied to a specific repository.</p>
     *
     * @param repositoryPath the absolute path to the root of the repository
     * @throws MissingRepositoryException if the path is {@code null} or does not contain a valid {@code .glit} directory
     */
    public ObjectReader(Path repositoryPath){
        if(repositoryPath == null || !Files.exists(repositoryPath.resolve(".glit"))){
            throw new MissingRepositoryException();
        }
        this.repositoryPath = repositoryPath;
    }

    /**
     * <p>Reads a compressed object from the storage database by its hash and reconstitutes
     * it into its specific concrete Java type.</p>
     *
     * <p>The retrieval and parsing process follows these technical steps:</p>
     * <ol>
     * <li>Resolves the file path using the two-character subdirectory convention based on the object's hash.</li>
     * <li>Opens a compressed file stream using {@link InflaterInputStream}.</li>
     * <li>Reads the header bytes sequentially until a null byte ({@code 0}) is encountered, which acts as the delimiter.</li>
     * <li>Extracts the object type token from the parsed header string.</li>
     * <li>Reads the remaining uncompressed bytes as the object's core content payload.</li>
     * <li>Instantiates and returns the correct subtype based on the extracted type identifier.</li>
     * </ol>
     *
     * @param hash the full unique hash string of the object to look up
     * @return the concrete {@link GlitObject} implementation ({@link Blob}, {@link Tree}, or {@link Commit})
     * @throws GlitException if the object file does not exist, the header structure is corrupted,
     * or an unknown object type is encountered
     */
    public GlitObject readObject(String hash){

        String dirName = hash.substring(0,2);
        String fileName = hash.substring(2);

        Path dirPath = repositoryPath.resolve(".glit/objects/").resolve(dirName);
        Path filePath = dirPath.resolve(fileName);

        if(!Files.exists(filePath)){
            throw new GlitException("No object with provided hash: " + hash);
        }

        try(InflaterInputStream iis = new InflaterInputStream(Files.newInputStream(filePath));)
        {

            ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
            int b;

            while ((b = iis.read()) != -1 && b != 0) {
                headerBuffer.write(b);
            }
            String header = headerBuffer.toString(StandardCharsets.UTF_8);
            String [] HeaderSplited = header.split(" ");
            String type = HeaderSplited[0];

            byte [] content = iis.readAllBytes();

            switch (type){
                case "blob":
                    return new Blob(content);
                case "tree":
                    return new Tree(content);
                case "commit":
                    return new Commit(content);
                default:
                    throw new GlitException("fatal: Wrong type of object");
            }


        } catch (IOException e) {
            throw new GlitException("fatal: Cannot read file content for hash " + hash,e);
        }

    }
}