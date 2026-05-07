package glit.cli;

import java.util.List;
import java.util.ArrayList;

public class Call {

    private final String function;
    private final List<String> flags;
    private final List<Object> arguments;

    public Call(String fx, List<String> fl, List<Object> args) {
        function = fx;
        flags = fl==null ? new ArrayList<String>() : new ArrayList<String>(fl);
        arguments = args==null ? new ArrayList<Object>() : new ArrayList<Object>(args);
    }

    public String getFunction() {
        return function;
    }
    public String toString(){
        String temp = function + "; flags{ ";
        for(String el : flags){ temp += el + " "; }
        temp += "}; arguments{ ";
        for(Object el : arguments){ temp += el + " "; }
        temp += "}";
        return temp;
    }

}
