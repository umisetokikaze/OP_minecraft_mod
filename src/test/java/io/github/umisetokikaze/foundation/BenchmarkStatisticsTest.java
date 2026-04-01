package io.github.umisetokikaze.foundation;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkStatisticsTest {
    @Test
    void returnsMiddleValueForOddSamples() {
        assertEquals(11.0D, BenchmarkStatistics.median(List.of(5.0D, 11.0D, 20.0D)));
    }

    @Test
    void returnsMeanOfTwoMiddleValuesForEvenSamples() {
        assertEquals(7.0D, BenchmarkStatistics.median(List.of(3.0D, 6.0D, 8.0D, 20.0D)));
    }

    @Test
    void sortsInputBeforeTakingMedian() {
        assertEquals(6.0D, BenchmarkStatistics.median(List.of(8.0D, 2.0D, 6.0D, 4.0D, 10.0D)));
    }

    @Test
    void rejectsEmptySamples() {
        assertThrows(IllegalArgumentException.class, () -> BenchmarkStatistics.median(List.of()));
    }
}
