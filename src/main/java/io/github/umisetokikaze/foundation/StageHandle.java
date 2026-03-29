package io.github.umisetokikaze.foundation;

public interface StageHandle extends AutoCloseable {
    @Override
    void close();
}
