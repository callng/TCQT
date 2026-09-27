package bsh.module;

import bsh.Interpreter;

public interface BshModule {
    String getId();

    void install(Interpreter interpreter);
}
