package net.godwand;

import net.godwand.entity.ArcaneOrbEntity;
import net.godwand.entity.StoneCannonProjectileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GodWandMod {
    public static final String MOD_ID = "godwand";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static EntityType<ArcaneOrbEntity> ARCANE_ORB_ENTITY_TYPE;
    public static EntityType<StoneCannonProjectileEntity> STONE_CANNON_ENTITY_TYPE;
    public static Item GOD_WAND_ITEM;
    public static final ResourceKey<CreativeModeTab> GODWAND_ITEM_GROUP_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(MOD_ID, "godwand_tab")
    );
    public static CreativeModeTab GODWAND_ITEM_GROUP;

    public static int colorFromVector3f(org.joml.Vector3f v) {
        return net.minecraft.util.ARGB.colorFromFloat(1.0f, v.x, v.y, v.z);
    }
}
