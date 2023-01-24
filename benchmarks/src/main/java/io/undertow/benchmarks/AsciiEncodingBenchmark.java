/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Measurement(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class AsciiEncodingBenchmark implements AsciiEncoders.BufferFlusher {

    private static final int ASCII_GEN_SEED = 0;
    @Param({"7", "19", "248", "12392", "493727"})
    private int inLength;
    @Param({"8196"})
    private int outCapacity;

    @Param({"batch", "noBatch", "vanilla"})
    private String encoderType;
    private ByteBuffer out;
    private char[] input;

    private AsciiEncoders.AsciiEncoder encoder;

    @Setup
    public void init() {
        out = ByteBuffer.allocateDirect(outCapacity).order(ByteOrder.BIG_ENDIAN);
        SplittableRandom random = new SplittableRandom(ASCII_GEN_SEED);
        input = new char[inLength];
        for (int i = 0; i < inLength; i++) {
            input[i] = (char) random.nextInt(0, 128);
        }
        switch (encoderType) {
            case "batch":
                encoder = AsciiEncoders.BatchFixedBufferOffsetAsciiEncoder.Instance;
                break;
            case "noBatch":
                encoder = AsciiEncoders.NonBatchFixedBufferOffsetAsciiEncoder.Instance;
                break;
            case "vanilla":
                encoder = AsciiEncoders.NonBatchMutableBufferOffsetAsciiEncoder.Instance;
                break;
        }
    }

    @Override
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void flushBuffer(ByteBuffer buffer) throws IOException {
        buffer.position(0);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int encode() throws IOException {
        final ByteBuffer out = this.out;
        final char[] input = this.input;
        out.clear();
        return encoder.writeAndFlushAscii(this, out, this.input, 0, input.length);
    }
}
