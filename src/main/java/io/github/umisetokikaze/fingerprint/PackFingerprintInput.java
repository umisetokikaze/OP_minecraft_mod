package io.github.umisetokikaze.fingerprint;

import java.util.List;
import java.util.Map;

public record PackFingerprintInput(
        String minecraftVersion,
        String neoForgeVersion,
        List<ModFingerprintEntry> mods,
        List<ResourcePackFingerprintEntry> resourcePacks,
        Map<String, String> relevantFileHashes,
        Map<String, String> settings
) {
    public PackFingerprintInput {
        mods = List.copyOf(mods);
        resourcePacks = List.copyOf(resourcePacks);
        relevantFileHashes = Map.copyOf(relevantFileHashes);
        settings = Map.copyOf(settings);
    }
}
