package glit.storage;


import glit.exceptions.GlitException;
import glit.exceptions.MissingRepositoryException;
import glit.model.GlitObject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;

public class ObjectWriter {

    private final Path repositoryPath;

    public ObjectWriter(Path repositoryPath){
        if(repositoryPath == null || !Files.exists(repositoryPath.resolve(".glit"))){
            throw new MissingRepositoryException();
        }
        this.repositoryPath = repositoryPath;
    }

    public String saveObject(GlitObject o){
        save(o.getHash(),o.getContentWithHeader());
        return o.getHash();
    }

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
