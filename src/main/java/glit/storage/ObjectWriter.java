package glit.storage;


import glit.model.GlitObject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;

public class ObjectWriter {

    private final Path repositoryPath;

    public ObjectWriter(Path repositoryPath)throws IOException{
        this.repositoryPath = repositoryPath;
        if(repositoryPath == null){
            throw new IOException("Cannot find glit repository!");
        }
    }

    public String saveObject(GlitObject o) throws IOException{
        save(o.getHash(),o.getContentWithHeader());
        return o.getHash();
    }

    private void save(String hash,byte[] contentWithHeader)throws IOException{

        String dirName = hash.substring(0,2);
        String fileName = hash.substring(2);

        Path dirPath = repositoryPath.resolve(".glit/objects/").resolve(dirName);
        Path filePath = dirPath.resolve(fileName);
        Files.createDirectories(dirPath);

        if(Files.exists(filePath)){
            return;
        }

        try(DeflaterOutputStream dos = new DeflaterOutputStream(Files.newOutputStream(filePath));
            BufferedOutputStream bos = new BufferedOutputStream(dos) )
        {
            bos.write(contentWithHeader);
        } catch (IOException e) {
            System.err.println("Cannot save file content");
            throw new RuntimeException(e);
        }

    }



}
