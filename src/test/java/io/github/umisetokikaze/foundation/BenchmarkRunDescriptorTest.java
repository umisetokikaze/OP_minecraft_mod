package io.github.umisetokikaze.foundation;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BenchmarkRunDescriptorTest {
    @AfterEach
    void clearProperties() {
        for (String key : Map.of(
                "momooptimizer.benchmark.caseId", "",
                "momooptimizer.benchmark.variant", "",
                "momooptimizer.benchmark.runIndex", "",
                "momooptimizer.benchmark.temperature", "",
                "momooptimizer.benchmark.worldId", "",
                "momooptimizer.benchmark.shaderEnabled", ""
        ).keySet()) {
            System.clearProperty(key);
        }
    }

    @Test
    void derivesDefaultsFromCaseId() {
        System.setProperty("momooptimizer.benchmark.caseId", "startup_cold");

        BenchmarkRunDescriptor descriptor = BenchmarkRunDescriptor.fromSystemProperties();

        assertNotNull(descriptor);
        assertEquals(BenchmarkCaseId.STARTUP_COLD, descriptor.caseId());
        assertEquals("candidate", descriptor.variant());
        assertEquals("cold", descriptor.expectedTemperature());
        assertEquals(1, descriptor.runIndex());
        assertEquals("unspecified", descriptor.worldId());
    }

    @Test
    void respectsExplicitOverrides() {
        System.setProperty("momooptimizer.benchmark.caseId", "world_join_existing");
        System.setProperty("momooptimizer.benchmark.variant", "baseline");
        System.setProperty("momooptimizer.benchmark.runIndex", "7");
        System.setProperty("momooptimizer.benchmark.temperature", "warm");
        System.setProperty("momooptimizer.benchmark.worldId", "benchmark-world-a");
        System.setProperty("momooptimizer.benchmark.shaderEnabled", "false");

        BenchmarkRunDescriptor descriptor = BenchmarkRunDescriptor.fromSystemProperties();

        assertNotNull(descriptor);
        assertEquals(BenchmarkCaseId.WORLD_JOIN_EXISTING, descriptor.caseId());
        assertEquals("baseline", descriptor.variant());
        assertEquals(7, descriptor.runIndex());
        assertEquals("warm", descriptor.expectedTemperature());
        assertEquals("benchmark-world-a", descriptor.worldId());
    }
}
