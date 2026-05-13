package glit.storage;

import glit.model.Blob;
import glit.model.Commit;
import glit.model.GlitObject;
import glit.model.Tree;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.zip.InflaterInputStream;

public class ObjectReader {

    private final Path repositoryPath;

    public ObjectReader(Path repositoryPath)throws IOException{
        this.repositoryPath = repositoryPath;
        if(repositoryPath == null){
            throw new IOException("Cannot find glit repository!");
        }
    }

    public GlitObject readObject(String hash){

        String dirName = hash.substring(0,2);
        String fileName = hash.substring(2);

        Path dirPath = repositoryPath.resolve(".glit/objects/").resolve(dirName);
        Path filePath = dirPath.resolve(fileName);

        if(!Files.exists(filePath)){
            throw new RuntimeException("No object with provided hash");
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
                    throw new RuntimeException("Wrong type of object");
            }


        } catch (IOException e) {
            System.err.println("Cannot read file content");
            throw new RuntimeException(e);
        }

    }
}