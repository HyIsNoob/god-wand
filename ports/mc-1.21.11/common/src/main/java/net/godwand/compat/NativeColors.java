package net.godwand.compat;

/** Converts authored particle RGB vectors to the native packed ARGB format. */
public final class NativeColors {
    public static int rgb(org.joml.Vector3f color) {
        return net.minecraft.util.ARGB.colorFromFloat(1, color.x, color.y, color.z);
    }
}
