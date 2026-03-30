package io.github.umisetokikaze.fingerprint;

public record ModFingerprintEntry(
        String modId,
        String version,
        String jarIdentifier,
        String jarFileHash
) {
}
