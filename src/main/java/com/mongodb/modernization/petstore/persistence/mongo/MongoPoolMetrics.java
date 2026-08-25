package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.event.ConnectionCheckOutFailedEvent;
import com.mongodb.event.ConnectionCheckOutStartedEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionClosedEvent;
import com.mongodb.event.ConnectionCreatedEvent;
import com.mongodb.event.ConnectionPoolCreatedEvent;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.modernization.petstore.observability.DatabaseDiagnostics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

class MongoPoolMetrics implements ConnectionPoolListener {
    private final AtomicInteger configuredMin = new AtomicInteger();
    private final AtomicInteger configuredMax = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger awaiting = new AtomicInteger();
    private final LongAdder acquisitionFailures = new LongAdder();
    private final LongAdder acquisitions = new LongAdder();
    private final LongAdder acquisitionNanos = new LongAdder();

    @Override public void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        configuredMin.addAndGet(event.getSettings().getMinSize());
        configuredMax.addAndGet(event.getSettings().getMaxSize());
    }

    @Override public void connectionCheckOutStarted(ConnectionCheckOutStartedEvent event) { awaiting.incrementAndGet(); }

    @Override public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
        awaiting.updateAndGet(value -> Math.max(0, value - 1));
        active.incrementAndGet();
        acquisitions.increment();
        acquisitionNanos.add(event.getElapsedTime(TimeUnit.NANOSECONDS));
    }

    @Override public void connectionCheckOutFailed(ConnectionCheckOutFailedEvent event) {
        awaiting.updateAndGet(value -> Math.max(0, value - 1));
        acquisitionFailures.increment();
        acquisitionNanos.add(event.getElapsedTime(TimeUnit.NANOSECONDS));
    }

    @Override public void connectionCheckedIn(ConnectionCheckedInEvent event) {
        active.updateAndGet(value -> Math.max(0, value - 1));
    }

    @Override public void connectionCreated(ConnectionCreatedEvent event) { total.incrementAndGet(); }

    @Override public void connectionClosed(ConnectionClosedEvent event) {
        total.updateAndGet(value -> Math.max(0, value - 1));
    }

    DatabaseDiagnostics.PoolSnapshot snapshot() {
        long checkoutCount = acquisitions.sum();
        int connectionCount = total.get();
        int checkedOut = active.get();
        return new DatabaseDiagnostics.PoolSnapshot("MongoDB Java Driver", configuredMin.get(), configuredMax.get(),
                connectionCount, checkedOut, Math.max(0, connectionCount - checkedOut), awaiting.get(),
                acquisitionFailures.sum(), checkoutCount == 0 ? 0
                : acquisitionNanos.sum() / 1_000_000.0 / checkoutCount);
    }
}
