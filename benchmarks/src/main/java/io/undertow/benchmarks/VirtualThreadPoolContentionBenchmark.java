package io.undertow.benchmarks;

import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.DefaultByteBufferPool2;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmark comparing DefaultByteBufferPool and DefaultByteBufferPool2
 * under high contention using virtual threads.
 *
 * Creates many tasks (numTasks) but limits concurrent execution to maxConcurrency
 * using a semaphore. This simulates realistic contention where many tasks compete
 * for limited concurrency slots.
 */
// Throughput mode - measures operations per second
//@BenchmarkMode(Mode.Throughput)
//@OutputTimeUnit(TimeUnit.SECONDS)

// Average time mode - measures time per operation
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)

@State(Scope.Benchmark)
@Fork(1)  // Single fork for consistency
@Warmup(iterations = 3, time = 2)  // 3 warmup iterations, 2 seconds each
@Measurement(iterations = 5, time = 3)  // 5 measurement iterations, 3 seconds each
@Threads(1)  // JMH thread count (we'll use virtual threads internally)
public class VirtualThreadPoolContentionBenchmark {

    private ByteBufferPool pool;
    private ExecutorService executor;
    private Semaphore concurrencyLimiter;
    private CountDownLatch startLatch;
    private CountDownLatch completionLatch;
    private AtomicInteger successCount;

    @Param({"DefaultByteBufferPool", "DefaultByteBufferPool2"})
    private String poolType;

    @Param({"16384"})  // Buffer sizes to test
    private int bufferSize;

    @Param({"8", "16", "32", "64"})  // Number of concurrent virtual threads allowed
    private int maxConcurrency;

    @Param({"100000"})  // Total number of tasks
    private int numTasks;

    @Param({"false", "true"})  // Whether to prefill the pool cache before benchmark
    //starts with a for column ordering purposes
    private boolean aPreFillCache;

    private static final int BUFFERS_PER_TASK = 1;
    private static final int MAXIMUM_POOL_SIZE = 1000;

    @Setup(Level.Trial)
    public void setupTrial() {
        // Create the appropriate pool based on parameter
        if ("DefaultByteBufferPool".equals(poolType)) {
            // DefaultByteBufferPool(direct, bufferSize, maxPoolSize, threadLocalCacheSize)
            pool = new DefaultByteBufferPool(true, bufferSize, MAXIMUM_POOL_SIZE, 0);
        } else if ("DefaultByteBufferPool2".equals(poolType)) {
            // DefaultByteBufferPool2(direct, bufferSize, maxPoolSize, threadLocalCacheSize)
            pool = new DefaultByteBufferPool2(true, bufferSize, MAXIMUM_POOL_SIZE, 0);
        } else {
            throw new IllegalArgumentException("Unknown pool type: " + poolType);
        }

        // Optionally prefill the pool cache
        if (aPreFillCache) {
            PooledByteBuffer[] buffers = new PooledByteBuffer[MAXIMUM_POOL_SIZE];

            // Allocate all buffers
            for (int i = 0; i < MAXIMUM_POOL_SIZE; i++) {
                buffers[i] = pool.allocate();
            }

            // Close all buffers (return them to the pool)
            for (int i = 0; i < MAXIMUM_POOL_SIZE; i++) {
                buffers[i].close();
            }
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        // Setup for each benchmark invocation
        concurrencyLimiter = new Semaphore(maxConcurrency, true);  // Fair semaphore
        startLatch = new CountDownLatch(1);
        completionLatch = new CountDownLatch(numTasks);
        successCount = new AtomicInteger(0);

        // Create virtual thread executor
        executor = Executors.newVirtualThreadPerTaskExecutor();

        // Submit all tasks (not measured - happens in setup)
        for (int i = 0; i < numTasks; i++) {
            executor.submit(() -> {
                try {
                    // Wait for benchmark to signal start
                    startLatch.await();

                    // Acquire permit (blocks if maxConcurrency threads are already running)
                    concurrencyLimiter.acquire();

                    try {
                        // Allocate and close buffers
                        PooledByteBuffer[] buffers = new PooledByteBuffer[BUFFERS_PER_TASK];

                        // Allocate all buffers
                        for (int j = 0; j < BUFFERS_PER_TASK; j++) {
                            buffers[j] = pool.allocate();
                            if (buffers[j] == null) {
                                throw new RuntimeException("Failed to allocate buffer");
                            }
                        }

                        // Do minimal work with buffers (just write a byte to each)
                        for (int j = 0; j < BUFFERS_PER_TASK; j++) {
                            buffers[j].getBuffer().put((byte) j);
                        }

                        // Close all buffers (return to pool)
                        for (int j = 0; j < BUFFERS_PER_TASK; j++) {
                            buffers[j].close();
                        }

                        successCount.incrementAndGet();
                    } finally {
                        // Always release the permit
                        concurrencyLimiter.release();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Prevent new tasks from being submitted
        executor.shutdown();
    }

    @Benchmark
    public int contentionTest() throws InterruptedException {
        // Signal all waiting threads to begin work (this is what we measure)
        startLatch.countDown();

        // Wait for all tasks to complete
        completionLatch.await();

        // Return total successful operations
        return successCount.get();
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() throws InterruptedException {
        if (executor != null) {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool = null;
    }

    // Main method to run the benchmark
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + VirtualThreadPoolContentionBenchmark.class.getSimpleName() + ".*")
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }
}