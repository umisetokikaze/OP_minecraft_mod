package io.github.umisetokikaze.foundation;

final class StageProfiler {
    private final FoundationStats stats;
    private final BenchmarkHarness benchmarkHarness;

    StageProfiler(FoundationStats stats, BenchmarkHarness benchmarkHarness) {
        this.stats = stats;
        this.benchmarkHarness = benchmarkHarness;
    }

    StageScope begin(String stageName) {
        return new StageScope(stageName, System.nanoTime(), Thread.currentThread().getName());
    }

    final class StageScope implements StageHandle {
        private final String stageName;
        private final long startedAtNanos;
        private final String threadName;
        private boolean closed;

        private StageScope(String stageName, long startedAtNanos, String threadName) {
            this.stageName = stageName;
            this.startedAtNanos = startedAtNanos;
            this.threadName = threadName;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            long durationNanos = System.nanoTime() - startedAtNanos;
            boolean mainThread = threadName.equals("Render thread") || threadName.equals("main");
            stats.recordStage(stageName, durationNanos, mainThread, threadName);
            benchmarkHarness.recordStage(stageName, durationNanos, mainThread, threadName);
        }
    }
}
