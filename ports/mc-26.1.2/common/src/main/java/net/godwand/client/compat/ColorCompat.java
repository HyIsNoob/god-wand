package net.godwand.client.compat;

import org.joml.Vector3f;

/** Dust colours as opaque ARGB ints for the 26.1 particle options. */
public final class ColorCompat {
    private ColorCompat() {}
    public static int fromVector3f(Vector3f rgb) {
        return net.minecraft.util.ARGB.colorFromFloat(1.0f, rgb.x(), rgb.y(), rgb.z());
    }
}