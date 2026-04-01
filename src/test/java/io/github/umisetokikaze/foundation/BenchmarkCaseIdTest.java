package io.github.umisetokikaze.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BenchmarkCaseIdTest {
    @Test
    void resolvesKnownValuesIgnoringCaseAndWhitespace() {
        assertEquals(BenchmarkCaseId.STARTUP_WARM, BenchmarkCaseId.fromValue(" startup_warm "));
        assertEquals(BenchmarkCaseId.WORLD_JOIN_EXISTING, BenchmarkCaseId.fromValue("WORLD_JOIN_EXISTING"));
    }

    @Test
    void returnsNullForBlankOrUnknownValues() {
        assertNull(BenchmarkCaseId.fromValue(null));
        assertNull(BenchmarkCaseId.fromValue("   "));
        assertNull(BenchmarkCaseId.fromValue("unknown_case"));
    }
}
