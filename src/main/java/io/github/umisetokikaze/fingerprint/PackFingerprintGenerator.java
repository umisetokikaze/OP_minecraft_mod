package io.github.umisetokikaze.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class PackFingerprintGenerator {
    public static final int SCHEMA_VERSION = 1;
    public static final String ALGORITHM = "SHA-256";

    private PackFingerprintGenerator() {
    }

    public static PackFingerprint generate(PackFingerprintInput input) {
        Objects.requireNonNull(input, "input");

        String canonicalPayload = canonicalize(input);
        return new PackFingerprint(
                SCHEMA_VERSION,
                ALGORITHM,
                canonicalPayload,
                sha256Hex(canonicalPayload)
        );
    }

    public static PackFingerprintComparison compare(PackFingerprintInput left, PackFingerprintInput right) {
        NormalizedInput normalizedLeft = normalize(left);
        NormalizedInput normalizedRight = normalize(right);

        List<String> differences = new ArrayList<>();

        if (!normalizedLeft.minecraftVersion.equals(normalizedRight.minecraftVersion)) {
            differences.add("minecraftVersion changed");
        }
        if (!normalizedLeft.neoForgeVersion.equals(normalizedRight.neoForgeVersion)) {
            differences.add("neoForgeVersion changed");
        }
        if (!normalizedLeft.mods.equals(normalizedRight.mods)) {
            differences.add("mods changed");
        }
        if (!normalizedLeft.resourcePacks.equals(normalizedRight.resourcePacks)) {
            differences.add("resourcePacks changed");
        }
        if (!normalizedLeft.relevantFileHashes.equals(normalizedRight.relevantFileHashes)) {
            differences.add("relevantFileHashes changed");
        }
        if (!normalizedLeft.settings.equals(normalizedRight.settings)) {
            differences.add("settings changed");
        }

        return new PackFingerprintComparison(differences.isEmpty(), differences);
    }

    public static String canonicalize(PackFingerprintInput input) {
        NormalizedInput normalized = normalize(input);
        StringBuilder builder = new StringBuilder(1024);

        appendScalar(builder, "schemaVersion", Integer.toString(SCHEMA_VERSION));
        appendScalar(builder, "algorithm", ALGORITHM);
        appendScalar(builder, "minecraftVersion", normalized.minecraftVersion);
        appendScalar(builder, "neoForgeVersion", normalized.neoForgeVersion);

        appendScalar(builder, "mods.count", Integer.toString(normalized.mods.size()));
        for (int i = 0; i < normalized.mods.size(); i++) {
            NormalizedModEntry mod = normalized.mods.get(i);
            appendScalar(builder, "mods[" + i + "].modId", mod.modId());
            appendScalar(builder, "mods[" + i + "].version", mod.version());
            appendScalar(builder, "mods[" + i + "].jarIdentifier", mod.jarIdentifier());
            appendScalar(builder, "mods[" + i + "].jarFileHash", mod.jarFileHash());
        }

        appendScalar(builder, "resourcePacks.count", Integer.toString(normalized.resourcePacks.size()));
        for (int i = 0; i < normalized.resourcePacks.size(); i++) {
            NormalizedResourcePackEntry pack = normalized.resourcePacks.get(i);
            appendScalar(builder, "resourcePacks[" + i + "].packId", pack.packId());
            appendScalar(builder, "resourcePacks[" + i + "].displayName", pack.displayName());
        }

        appendScalar(builder, "relevantFileHashes.count", Integer.toString(normalized.relevantFileHashes.size()));
        for (Map.Entry<String, String> entry : normalized.relevantFileHashes.entrySet()) {
            appendScalar(builder, "relevantFileHashes[" + entry.getKey() + "]", entry.getValue());
        }

        appendScalar(builder, "settings.count", Integer.toString(normalized.settings.size()));
        for (Map.Entry<String, String> entry : normalized.settings.entrySet()) {
            appendScalar(builder, "settings[" + entry.getKey() + "]", entry.getValue());
        }

        return builder.toString();
    }

    private static NormalizedInput normalize(PackFingerprintInput input) {
        Objects.requireNonNull(input, "input");

        List<NormalizedModEntry> normalizedMods = input.mods().stream()
                .map(mod -> new NormalizedModEntry(
                        normalizeString(mod.modId()),
                        normalizeString(mod.version()),
                        normalizeString(mod.jarIdentifier()),
                        normalizeHash(mod.jarFileHash())
                ))
                .sorted(Comparator
                        .comparing(NormalizedModEntry::modId)
                        .thenComparing(NormalizedModEntry::version)
                        .thenComparing(NormalizedModEntry::jarIdentifier)
                        .thenComparing(NormalizedModEntry::jarFileHash))
                .toList();

        List<NormalizedResourcePackEntry> normalizedResourcePacks = input.resourcePacks().stream()
                .map(pack -> new NormalizedResourcePackEntry(
                        normalizeString(pack.packId()),
                        normalizeString(pack.displayName())
                ))
                .toList();

        Map<String, String> normalizedRelevantFiles = new TreeMap<>();
        input.relevantFileHashes().forEach((path, hash) -> normalizedRelevantFiles.put(normalizePath(path), normalizeHash(hash)));

        Map<String, String> normalizedSettings = new TreeMap<>();
        input.settings().forEach((key, value) -> normalizedSettings.put(normalizeString(key), normalizeString(value)));

        return new NormalizedInput(
                normalizeString(input.minecraftVersion()),
                normalizeString(input.neoForgeVersion()),
                normalizedMods,
                normalizedResourcePacks,
                normalizedRelevantFiles,
                normalizedSettings
        );
    }

    private static void appendScalar(StringBuilder builder, String key, String value) {
        builder.append(escape(key))
                .append('=')
                .append(escape(value))
                .append('\n');
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '=' -> builder.append("\\=");
                default -> builder.append(current);
            }
        }
        return builder.toString();
    }

    private static String normalizeString(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    }

    private static String normalizePath(String value) {
        String normalized = normalizeString(value).replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static String normalizeHash(String value) {
        return normalizeString(value).toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                builder.append(Character.forDigit((hashByte >>> 4) & 0xF, 16));
                builder.append(Character.forDigit(hashByte & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing digest algorithm: " + ALGORITHM, exception);
        }
    }

    private record NormalizedInput(
            String minecraftVersion,
            String neoForgeVersion,
            List<NormalizedModEntry> mods,
            List<NormalizedResourcePackEntry> resourcePacks,
            Map<String, String> relevantFileHashes,
            Map<String, String> settings
    ) {
    }

    private record NormalizedModEntry(
            String modId,
            String version,
            String jarIdentifier,
            String jarFileHash
    ) {
    }

    private record NormalizedResourcePackEntry(
            String packId,
            String displayName
    ) {
    }
}
