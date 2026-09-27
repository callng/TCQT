package bsh.snapshot;

import bsh.Node;
import java.io.Serializable;

public final class BshSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int FORMAT_VERSION = 1;

    private final int formatVersion;
    private final Node[] nodes;

    public BshSnapshot(Node[] nodes) {
        this.formatVersion = FORMAT_VERSION;
        this.nodes = nodes;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public Node[] getNodes() {
        return nodes;
    }
}
