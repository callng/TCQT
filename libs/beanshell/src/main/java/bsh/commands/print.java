package bsh.commands;

import bsh.CallStack;
import bsh.Interpreter;

public class print {
    /**
     * Implement print( Object message ) command.
     */
    public static void invoke(Interpreter interpreter, CallStack callStack, Object message) {
        interpreter.println(message);
    }
}
