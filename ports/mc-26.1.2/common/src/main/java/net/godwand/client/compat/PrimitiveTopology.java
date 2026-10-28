package net.godwand.client.compat;

import com.mojang.blaze3d.vertex.VertexFormat;

public enum PrimitiveTopology {
    QUADS(VertexFormat.Mode.QUADS),
    TRIANGLES(VertexFormat.Mode.TRIANGLES),
    TRIANGLE_STRIP(VertexFormat.Mode.TRIANGLE_STRIP),
    TRIANGLE_FAN(VertexFormat.Mode.TRIANGLE_FAN);

    public final VertexFormat.Mode mode;

    PrimitiveTopology(VertexFormat.Mode mode) {
        this.mode = mode;
    }

    public static PrimitiveTopology fromMode(VertexFormat.Mode mode) {
        return switch (mode) {
            case QUADS -> QUADS;
            case TRIANGLES -> TRIANGLES;
            case TRIANGLE_STRIP -> TRIANGLE_STRIP;
            case TRIANGLE_FAN -> TRIANGLE_FAN;
            default -> throw new IllegalArgumentException("Unsupported vertex mode: " + mode);
        };
    }
}
