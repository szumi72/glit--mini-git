package glit.exceptions;

public class MissingRepositoryException extends GlitException{
    public MissingRepositoryException(){
        super("\"Not a glit repository (or any of the parent directories): .glit\"");
    }
}
