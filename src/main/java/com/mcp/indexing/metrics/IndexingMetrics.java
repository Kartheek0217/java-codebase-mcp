package com.mcp.indexing.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class IndexingMetrics {
    private final Timer indexWriteLatency;
    private final Counter parseErrors;
    private final AtomicInteger queueDepth;

    public IndexingMetrics(MeterRegistry registry) {
        this.indexWriteLatency = Timer.builder("index.write.latency")
                .description("Latency of index write operations")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry);

        this.parseErrors = Counter.builder("parse.errors")
                .description("Number of parse errors")
                .register(registry);

        this.queueDepth = new AtomicInteger(0);
        Gauge.builder("index.queue.depth", queueDepth, AtomicInteger::get)
                .description("Durable queue depth for indexing events")
                .register(registry);
    }

    public Timer.Sample startIndexWrite() {
        return Timer.start();
    }

    public void stopIndexWrite(Timer.Sample sample) {
        sample.stop(indexWriteLatency);
    }

    public void incrementParseError() {
        parseErrors.increment();
    }

    public void setQueueDepth(int value) {
        queueDepth.set(value);
    }
}

