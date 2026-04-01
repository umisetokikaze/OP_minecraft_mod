package io.github.umisetokikaze.foundation;

import io.github.umisetokikaze.Config;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonObject;

final class BenchmarkRunDescriptor {
    private static final String PREFIX = "momooptimizer.benchmark.";

    private final BenchmarkCaseId caseId;
    private final String variant;
    private final int runIndex;
    private final String expectedTemperature;
    private final String worldId;
    private final boolean shaderEnabled;
    private final long createdAtNanos;
    private final String jvmArgsHash;
    private final String configHash;

    private BenchmarkRunDescriptor(
            BenchmarkCaseId caseId,
            String variant,
            int runIndex,
            String expectedTemperature,
            String worldId,
            boolean shaderEnabled,
            long createdAtNanos,
            String jvmArgsHash,
            String configHash) {
        this.caseId = caseId;
        this.variant = variant;
        this.runIndex = runIndex;
        this.expectedTemperature = expectedTemperature;
        this.worldId = worldId;
        this.shaderEnabled = shaderEnabled;
        this.createdAtNanos = createdAtNanos;
        this.jvmArgsHash = jvmArgsHash;
        this.configHash = configHash;
    }

    static BenchmarkRunDescriptor fromSystemProperties() {
        BenchmarkCaseId caseId = BenchmarkCaseId.fromValue(System.getProperty(PREFIX + "caseId"));
        if (caseId == null) {
            return null;
        }

        String variant = normalizeVariant(System.getProperty(PREFIX + "variant", "candidate"));
        int runIndex = parseInt(System.getProperty(PREFIX + "runIndex"), 1);
        String expectedTemperature = normalizeTemperature(
                System.getProperty(PREFIX + "temperature", defaultTemperature(caseId)));
        String worldId = normalizeWorldId(System.getProperty(PREFIX + "worldId", "unspecified"));
        boolean shaderEnabled = Boolean.parseBoolean(System.getProperty(PREFIX + "shaderEnabled", "false"));

        return new BenchmarkRunDescriptor(
                caseId,
                variant,
                Math.max(runIndex, 1),
                expectedTemperature,
                worldId,
                shaderEnabled,
                System.nanoTime(),
                sha256Hex(String.join("\n", ManagementFactory.getRuntimeMXBean().getInputArguments())),
                loadConfigHash());
    }

    BenchmarkCaseId caseId() {
        return caseId;
    }

    String variant() {
        return variant;
    }

    int runIndex() {
        return runIndex;
    }

    String expectedTemperature() {
        return expectedTemperature;
    }

    String worldId() {
        return worldId;
    }

    boolean shaderEnabled() {
        return shaderEnabled;
    }

    long createdAtNanos() {
        return createdAtNanos;
    }

    String jvmArgsHash() {
        return jvmArgsHash;
    }

    String configHash() {
        return configHash;
    }

    JsonObject toJson(PackFingerprintSnapshot snapshot) {
        JsonObject json = new JsonObject();
        json.addProperty("caseId", caseId.value());
        json.addProperty("variant", variant);
        json.addProperty("runIndex", runIndex);
        json.addProperty("expectedTemperature", expectedTemperature);
        json.addProperty("preflightFingerprint", snapshot.fingerprint());
        json.addProperty("resourcePackFingerprint", snapshot.fingerprint());
        json.addProperty("configHash", configHash);
        json.addProperty("jvmArgsHash", jvmArgsHash);
        json.addProperty("shaderEnabled", shaderEnabled);
        json.addProperty("worldId", worldId);
        return json;
    }

    private static String defaultTemperature(BenchmarkCaseId caseId) {
        return switch (caseId) {
            case STARTUP_COLD -> "cold";
            case STARTUP_WARM -> "warm";
            default -> "warm";
        };
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String normalizeVariant(String value) {
        String normalized = value == null ? "candidate" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "candidate" : normalized;
    }

    private static String normalizeTemperature(String value) {
        String normalized = value == null ? "warm" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("cold") && !normalized.equals("warm")) {
            return "warm";
        }
        return normalized;
    }

    private static String normalizeWorldId(String value) {
        String normalized = value == null ? "unspecified" : value.trim();
        return normalized.isBlank() ? "unspecified" : normalized;
    }

    private static String canonicalizeConfigInputs(Map<String, String> inputs) {
        Map<String, String> sorted = new TreeMap<>(inputs);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return builder.toString();
    }

    private static String loadConfigHash() {
        try {
            return sha256Hex(canonicalizeConfigInputs(Config.diagnosticsInputs()));
        } catch (RuntimeException | LinkageError exception) {
            return sha256Hex("");
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 support", exception);
        }
    }
}
