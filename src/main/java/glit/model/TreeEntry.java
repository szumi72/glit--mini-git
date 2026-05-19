package glit.model;

/**
 * TreeEntry record
 * @param mode Mode of the file(regular = 100644,dictionary = 040000)
 * @param hash Hash of the added object
 * @param fileName Name of the file
 */
public record TreeEntry(String mode,String hash,String fileName){
    @Override
    public String toString(){
        return mode+ " " + hash + " " + fileName;
    }
}