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

public class ObjectReader {

    private final Path repositoryPath;

    public ObjectReader(Path repositoryPath){
        if(repositoryPath == null || !Files.exists(repositoryPath.resolve(".glit"))){
            throw new MissingRepositoryException();
        }
        this.repositoryPath = repositoryPath;
    }

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