package glit.cli;

import java.util.List;
import java.util.ArrayList;

/**
 * <p>Represents a parsed command-line invocation within the Glit ecosystem.</p>
 *
 * <p>This object serves as a simple Data Transfer Object (DTO) that encapsulates
 * the structural components of a CLI command: the target action/function name,
 * execution flags (switches), and any additional positional arguments.</p>
 */
public class Call {

    private final String function;
    private final List<String> flags;
    private final List<Object> arguments;

    /**
     * <p>Constructs a new {@code Call} execution object with null-safe collection handling.</p>
     *
     * @param fx   the name of the target function or command to execute (e.g., "checkout", "commit")
     * @param fl   the list of command flags/switches; if {@code null}, an empty list is initialized
     * @param args the list of positional arguments; if {@code null}, an empty list is initialized
     */
    public Call(String fx, List<String> fl, List<Object> args) {
        function = fx;
        flags = fl == null ? new ArrayList<>() : new ArrayList<>(fl);
        arguments = args == null ? new ArrayList<>() : new ArrayList<>(args);
    }

    /**
     * <p>Returns the name of the invoked operation or command.</p>
     *
     * @return a {@link String} representing the function identifier
     */
    public String getFunction() {
        return function;
    }

    /**
     * <p>Returns the positional arguments supplied to the command.</p>
     *
     * @return a {@link List} of objects representing the positional parameters
     */
    public List<Object> getArguments() {
        return arguments;
    }

    /**
     * <p>Returns the execution flags or configuration switches passed alongside the command.</p>
     *
     * @return a {@link List} of strings representing the flags (e.g., "b")
     */
    public List<String> getFlags() {
        return flags;
    }

    /**
     * <p>Returns a formatted string representation of the parsed CLI command,
     * useful for internal debugging and logging.</p>
     *
     * @return a debugging {@link String} revealing the function name, its flags, and arguments
     */
    @Override
    public String toString() {
        String temp = function + "; flags{ ";
        for (String el : flags) {
            temp += el + " ";
        }
        temp += "}; arguments{ ";
        for (Object el : arguments) {
            temp += el + " ";
        }
        temp += "}";
        return temp;
    }

}