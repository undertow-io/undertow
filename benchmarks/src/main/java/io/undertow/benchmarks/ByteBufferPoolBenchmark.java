package io.undertow.benchmarks;

import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.DefaultByteBufferPool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Simple JMH benchmark for Undertow's DefaultByteBufferPool.allocate()
 *
 * This benchmark measures the throughput of buffer allocation from the pool.
 */
@BenchmarkMode(Mode.Throughput)  // Measure operations per second
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(0)  // No forking - run in same JVM
@Warmup(iterations = 3, time = 1)  // 3 warmup iterations, 1 second each
@Measurement(iterations = 5, time = 1)  // 5 measurement iterations, 1 second each
public class ByteBufferPoolBenchmark {

    private DefaultByteBufferPool pool;

    @Param({"1024", "4096", "8192"})  // Different buffer sizes to test
    private int bufferSize;

    @Setup
    public void setup() {
        // Create a DefaultByteBufferPool with:
        // - direct buffers (true)
        // - specified buffer size
        // - max pool size of 100 buffers per thread
        // - thread local cache size of 12
        pool = new DefaultByteBufferPool(true, bufferSize, 100, 12);
    }

    @Benchmark
    public ByteBuffer allocate() {
        // Allocate a buffer from the pool
        PooledByteBuffer pooledBuffer = pool.allocate();

        // Get the actual ByteBuffer
        ByteBuffer buffer = pooledBuffer.getBuffer();

        // Important: Return the buffer to the pool
        pooledBuffer.close();

        // Return the buffer for JMH (prevents dead code elimination)
        return buffer;
    }

    @TearDown
    public void tearDown() {
        // Cleanup if needed (pool doesn't have explicit close method)
        pool = null;
    }

    // Main method to run the benchmark
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + ByteBufferPoolBenchmark.class.getSimpleName() + ".*")
                .forks(0)  // No forking - run in same JVM
                .warmupIterations(3)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }
}