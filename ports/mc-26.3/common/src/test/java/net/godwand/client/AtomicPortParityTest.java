package net.godwand.client;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class AtomicPortParityTest {
    private static String normalized(String s) {
        return s.replace("world.getMaxBuildHeight()-1","world.getMaxY()")
                .replace("world.getMaxBuildHeight()","(world.getMaxY() + 1)")
                .replace("world.getMinBuildHeight()","world.getMinY()").replaceAll("\\s+","");
    }
    @Test void portKeepsCanonicalAtomicTimingAndGeometry() throws Exception {
        Path canonical=Path.of("../../..").toAbsolutePath().normalize().resolve("common/src/main/java/net/godwand");
        Path port=Path.of("../../../build/shared/mc-26.3/src/main/java/net/godwand");
        for(String name:new String[]{"skill/AtomicSequence.java","skill/AtomicCrater.java"})
            assertEquals(normalized(Files.readString(canonical.resolve(name))),
                    normalized(Files.readString(port.resolve(name))),name);
    }
}
