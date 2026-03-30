package io.github.umisetokikaze.fingerprint;

import java.util.List;

public record PackFingerprintComparison(
        boolean identical,
        List<String> differences
) {
    public PackFingerprintComparison {
        differences = List.copyOf(differences);
    }
}
