package net.godwand.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minecraft 1.21.6 and newer draw a screen's background (including the menu blur) before they call the screen's own
 * render method. Drawing it a second time throws "Can only blur once per frame" and crashes the game as soon as the
 * configuration menu opens, so those targets must never draw the background from the screen class. Older targets do not
 * draw it for us and still have to.
 */
class ConfigScreenBackgroundTest {
    private static final Pattern BACKGROUND_CALL = Pattern.compile("\\b(?:render|extract)(?:Blurred|Transparent)?Background\\s*\\(");

    private static String source(String profile) throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path file = root.resolve("build/shared/" + profile + "/src/main/java/net/godwand/client/gui/GodWandConfigScreen.java");
        return Files.readString(file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    @Test
    void frameworkDrawnBackgroundIsNotDrawnAgainByTheConfigScreen() throws Exception {
        for (String profile : new String[]{"mc-1.21.11", "mc-26.1.2", "mc-26.3"}) {
            assertFalse(BACKGROUND_CALL.matcher(source(profile)).find(),
                    profile + ": the game already draws the background and blur once per frame; a second call crashes with "
                            + "'Can only blur once per frame' when the configuration menu opens");
        }
    }

    @Test
    void targetsThatDoNotDrawTheBackgroundForTheScreenStillDrawIt() throws Exception {
        for (String profile : new String[]{"fabric-1.20.1", "forge-1.20.1"}) {
            assertTrue(BACKGROUND_CALL.matcher(source(profile)).find(), profile + ": the config screen needs its own background");
        }
    }
}
