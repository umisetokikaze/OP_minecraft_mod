package io.github.umisetokikaze.foundation;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkStatisticsTest {
    @Test
    void returnsMiddleValueForOddSamples() {
        assertEquals(11.0D, BenchmarkStatistics.median(List.of(5.0D, 11.0D, 20.0D)));
    }

    @Test
    void returnsMeanOfTwoMiddleValuesForEvenSamples() {
        assertEquals(7.0D, BenchmarkStatistics.median(List.of(3.0D, 6.0D, 8.0D, 20.0D)));
    }
}
