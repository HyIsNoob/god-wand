package net.godwand.item;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.ShapedRecipe;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GodWandRecipeTest {
    @Test void recipeUsesNativeIngredientFormatAndUnlocksFromNetherStar() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        Path data=Path.of("../../../build/shared/mc-26.3/src/main/resources/data/godwand");
        var recipe=JsonParser.parseString(Files.readString(data.resolve("recipe/god_wand.json"))).getAsJsonObject();
        assertEquals("godwand:god_wand",recipe.getAsJsonObject("result").get("id").getAsString());
        recipe.getAsJsonObject("result").addProperty("id","minecraft:stick");
        var ops=RegistryOps.create(JsonOps.INSTANCE,net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        assertNotNull(ShapedRecipe.MAP_CODEC.codec().parse(ops,recipe).getOrThrow());
        var unlock=JsonParser.parseString(Files.readString(data.resolve("advancement/recipes/combat/god_wand.json"))).getAsJsonObject();
        assertEquals("godwand:god_wand",unlock.getAsJsonObject("criteria").getAsJsonObject("has_the_recipe").getAsJsonObject("conditions").get("recipes").getAsString());
    }
}
