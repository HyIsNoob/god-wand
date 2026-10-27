package net.godwand.config;

import net.minecraft.network.chat.Component;

public enum WandSkin {
    AUTO("auto", "skin.godwand.auto"),
    DEFAULT("default", "skin.godwand.default"),
    AQUA_HEARTIA("aqua_heartia", "skin.godwand.aqua_heartia"),
    SHADOW_BLADE("shadow_blade", "skin.godwand.shadow_blade");

    private final String id;
    private final String translationKey;

    WandSkin(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public static WandSkin fromId(String id) {
        for (WandSkin skin : values()) {
            if (skin.id.equalsIgnoreCase(id)) return skin;
        }
        return AUTO;
    }
}
