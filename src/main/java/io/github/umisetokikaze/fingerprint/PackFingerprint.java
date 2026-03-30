package io.github.umisetokikaze.fingerprint;

public record PackFingerprint(
        int schemaVersion,
        String algorithm,
        String canonicalPayload,
        String digestHex
) {
}
