/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class DefaultByteBufferPoolBenchmark {

    @Param({"false", "true"})
    private boolean direct;

    @Param({"0", "6", "12"})
    private int cacheSize;

    private DefaultByteBufferPool pool;

    @Setup
    public void setup() {
        pool = new DefaultByteBufferPool(direct, 16 * 1024 - 20, 1024, cacheSize);
    }

    @TearDown
    public void tearDown() {
        pool.close();
        pool.getArrayBackedPool().close();
    }

    @Benchmark
    public int allocateAndRelease(Buffers state) {
        int sum = 0;
        try {
            for (int i = 0; i < state.buffers.length; i++) {
                PooledByteBuffer buffer = pool.allocate();
                state.buffers[i] = buffer;
                buffer.getBuffer().putInt(i);
            }
            for (PooledByteBuffer buffer : state.buffers) {
                sum += buffer.getBuffer().getInt(0);
            }
            return sum;
        } finally {
            for (int i = 0; i < state.buffers.length; i++) {
                if (state.buffers[i] != null) {
                    state.buffers[i].close();
                    state.buffers[i] = null;
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class Buffers {

        @Param({"1", "16"})
        private int batchSize;

        private PooledByteBuffer[] buffers;

        @Setup
        public void setup() {
            buffers = new PooledByteBuffer[batchSize];
        }
    }
}
