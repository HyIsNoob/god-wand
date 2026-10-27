package net.godwand.item;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GodWandRecipeTest {
    @Test void bothLegacyPortsKeepTheExistingRecipeAndRecipeBookUnlock() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path shared=root.resolve("common/src/main/resources/data/godwand");
        var recipe=JsonParser.parseString(Files.readString(shared.resolve("recipe/god_wand.json"))).getAsJsonObject();
        var unlock=JsonParser.parseString(Files.readString(shared.resolve("advancement/recipes/combat/god_wand.json")));
        assertEquals("godwand:god_wand",recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals("minecraft:crafting_shaped",recipe.get("type").getAsString());
        recipe.getAsJsonObject("result").remove("id");
        recipe.getAsJsonObject("result").addProperty("item","godwand:god_wand");
        for(String port:new String[]{"fabric-1.20.1","forge-1.20.1"}) {
            Path data=root.resolve(port+"/src/main/resources/data/godwand");
            assertEquals(recipe,JsonParser.parseString(Files.readString(data.resolve("recipes/god_wand.json"))),port);
            assertEquals(unlock,JsonParser.parseString(Files.readString(data.resolve("advancements/recipes/combat/god_wand.json"))),port);
        }
    }
}
