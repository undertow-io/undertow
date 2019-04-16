/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @author Carter Kozak
 */
@Measurement(iterations = 3, time = 3)
@Warmup(iterations = 3, time = 3)
@Fork(1)
@Threads(32)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SimpleBenchmarks {

    @Benchmark
    public void benchmarkBlockingEmptyGet(SimpleBenchmarkState state) throws IOException {
        try (CloseableHttpResponse response = state.client()
                .execute(new HttpGet(state.getBaseUri() + "/blocking?size=0"))) {
            validateLength(response, 0L);
        }
    }

    @Benchmark
    public void benchmarkBlockingLargeGet(SimpleBenchmarkState state) throws IOException {
        try (CloseableHttpResponse response = state.client()
                .execute(new HttpGet(state.getBaseUri() + "/blocking?size=256000"))) {
            validateLength(response, 256000L);
        }
    }

    @Benchmark
    public void benchmarkBlockingEmptyPost(SimpleBenchmarkState state) throws IOException {
        try (CloseableHttpResponse response = state.client()
                .execute(new HttpPost(state.getBaseUri() + "/blocking"))) {
            String result = asString(validate(response).getEntity());
            if (!"0".equals(result)) {
                throw new IllegalStateException("expected 0, was " + result);
            }
        }
    }

    @Benchmark
    public void benchmarkBlockingLargePost(SimpleBenchmarkState state) throws IOException {
        HttpPost post = new HttpPost(state.getBaseUri() + "/blocking");
        post.setEntity(new InputStreamEntity(new StubInputStream(256000)));
        try (CloseableHttpResponse response = state.client().execute(post)) {
            String result = asString(validate(response).getEntity());
            if (!"256000".equals(result)) {
                throw new IllegalStateException("expected 256000, was " + result);
            }
        }
    }

    private String asString(HttpEntity entity) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        entity.writeTo(baos);
        return baos.toString("UTF-8");
    }

    private void validateLength(HttpResponse response, long expectedLength) throws IOException {
        long length = BenchmarkUtils.length(validate(response).getEntity().getContent());
        if (length != expectedLength) {
            throw new IllegalStateException("Unexpected length " + length);
        }
    }

    private <T extends HttpResponse> T validate(T response) {
        int status = response.getStatusLine().getStatusCode();
        if (status != 200) {
            throw new IllegalStateException("Unexpected status code " + status);
        }
        return response;
    }

    private static final class StubInputStream extends InputStream {

        private int bytes;

        StubInputStream(int bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read() {
            if (bytes <= 0) {
                return -1;
            }
            bytes--;
            return 1;
        }

        @Override
        public int available() {
            return bytes;
        }
    }
}
