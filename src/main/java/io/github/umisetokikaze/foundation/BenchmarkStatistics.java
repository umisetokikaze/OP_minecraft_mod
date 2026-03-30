package io.github.umisetokikaze.foundation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class BenchmarkStatistics {
    private BenchmarkStatistics() {
    }

    static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0D;
    }
}
