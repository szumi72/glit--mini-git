package glit.model;


public abstract class GlitObject{
    //unikalny Hash licozny za pomocą SHA-1
    protected String hash;
    //typ pliku(blob)(normalny = 100644, wykonywalny = 100755, link = 120000)
    protected String mode="-";

    public String getHash(){return hash;}
    public void setHash(String hash) {
        this.hash = hash;
    }

    //zwraca typ danych(blob,tree,commit)
    public abstract String getType();

    public String getMode(){
        return mode;
    }

    @Override
    public String toString(){
        return getType()+ " " + hash;
    }
    }