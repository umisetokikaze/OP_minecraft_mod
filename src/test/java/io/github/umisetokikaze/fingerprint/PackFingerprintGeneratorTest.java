package io.github.umisetokikaze.fingerprint;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackFingerprintGeneratorTest {
    @Test
    void generatesSameFingerprintForEquivalentInputs() {
        PackFingerprintInput left = new PackFingerprintInput(
                "1.21.1",
                "21.1.122",
                List.of(
                        new ModFingerprintEntry("example", "2.0.0", "mods/example.jar", "ABCDEF"),
                        new ModFingerprintEntry("another", "1.0.0", "mods/another.jar", "123456")
                ),
                List.of(
                        new ResourcePackFingerprintEntry("vanilla", "Vanilla"),
                        new ResourcePackFingerprintEntry("high_contrast", "High Contrast")
                ),
                Map.of(
                        "assets\\example\\models\\block\\stone.json", "FFAA",
                        "./assets/example/blockstates/stone.json", "BEEF"
                ),
                Map.of(
                        "cache.enabled", "true",
                        "cache.size", "256"
                )
        );

        PackFingerprintInput right = new PackFingerprintInput(
                "1.21.1",
                "21.1.122",
                List.of(
                        new ModFingerprintEntry("another", "1.0.0", "mods/another.jar", "123456"),
                        new ModFingerprintEntry("example", "2.0.0", "mods/example.jar", "abcdef")
                ),
                List.of(
                        new ResourcePackFingerprintEntry("vanilla", "Vanilla"),
                        new ResourcePackFingerprintEntry("high_contrast", "High Contrast")
                ),
                Map.of(
                        "assets/example/models/block/stone.json", "ffaa",
                        "assets/example/blockstates/stone.json", "beef"
                ),
                Map.of(
                        "cache.size", "256",
                        "cache.enabled", "true"
                )
        );

        assertEquals(PackFingerprintGenerator.generate(left).digestHex(), PackFingerprintGenerator.generate(right).digestHex());
        assertTrue(PackFingerprintGenerator.compare(left, right).identical());
    }

    @Test
    void changesFingerprintWhenResourcePackOrderChanges() {
        PackFingerprintInput left = sampleInput(List.of(
                new ResourcePackFingerprintEntry("vanilla", "Vanilla"),
                new ResourcePackFingerprintEntry("override", "Override")
        ));
        PackFingerprintInput right = sampleInput(List.of(
                new ResourcePackFingerprintEntry("override", "Override"),
                new ResourcePackFingerprintEntry("vanilla", "Vanilla")
        ));

        assertFalse(PackFingerprintGenerator.generate(left).digestHex().equals(PackFingerprintGenerator.generate(right).digestHex()));
        assertTrue(PackFingerprintGenerator.compare(left, right).differences().contains("resourcePacks changed"));
    }

    @Test
    void changesFingerprintWhenRelevantContentChanges() {
        PackFingerprintInput left = sampleInput(
                List.of(new ResourcePackFingerprintEntry("vanilla", "Vanilla")),
                Map.of("assets/example/models/item/a.json", "1111"),
                Map.of("cache.enabled", "true")
        );
        PackFingerprintInput right = sampleInput(
                List.of(new ResourcePackFingerprintEntry("vanilla", "Vanilla")),
                Map.of("assets/example/models/item/a.json", "2222"),
                Map.of("cache.enabled", "true")
        );

        PackFingerprintComparison comparison = PackFingerprintGenerator.compare(left, right);

        assertFalse(comparison.identical());
        assertTrue(comparison.differences().contains("relevantFileHashes changed"));
    }

    private static PackFingerprintInput sampleInput(List<ResourcePackFingerprintEntry> packs) {
        return sampleInput(packs, Map.of("assets/example/models/item/a.json", "1111"), Map.of("cache.enabled", "true"));
    }

    private static PackFingerprintInput sampleInput(
            List<ResourcePackFingerprintEntry> packs,
            Map<String, String> relevantFiles,
            Map<String, String> settings
    ) {
        return new PackFingerprintInput(
                "1.21.1",
                "21.1.122",
                List.of(new ModFingerprintEntry("example", "1.0.0", "mods/example.jar", "abcd")),
                packs,
                relevantFiles,
                settings
        );
    }
}
