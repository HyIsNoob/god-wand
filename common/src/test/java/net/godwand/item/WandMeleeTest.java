package net.godwand.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WandMeleeTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void wandAddsFourAttackDamageForFiveTotalIncludingThePlayersBaseDamage() {
        var entries=GodWandItem.createMeleeAttributes().modifiers();
        var attack=entries.stream().filter(e -> e.attribute().equals(Attributes.ATTACK_DAMAGE)).toList();
        assertEquals(1,attack.size());
        assertEquals(4,attack.getFirst().modifier().amount());
        assertEquals(net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND,attack.getFirst().slot());
    }
}
