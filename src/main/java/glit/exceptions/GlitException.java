package glit.exceptions;

public class GlitException extends RuntimeException{
    public GlitException(){
        super();
    }
    public GlitException(String mes){
        super(mes);
    }
    public GlitException(String mes, Throwable cause){
        super(mes,cause);
    }
}
