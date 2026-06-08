package glit.exceptions;

public class MergeConflictException extends GlitException{
    public MergeConflictException(String mes){
        super("\nMerge Conflict: " + mes);
    }

    public MergeConflictException(){
        super("\nMerge Conflict");
    }

}