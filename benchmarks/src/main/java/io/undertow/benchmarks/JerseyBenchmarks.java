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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.conduits.DeflatingStreamSinkConduit;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.util.ObjectPool;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.Deflater;

/**
 * @author Carter Kozak
 */
@Measurement(iterations = 5, time = 5)
@Warmup(iterations = 5, time = 5)
@Fork(value = 5, jvmArgs = {"-Xmx2g", "-Xms2g", "-XX:+UseParallelGC"})
@Threads(32)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class JerseyBenchmarks {

    private Undertow server;
    private CloseableHttpClient client;

    private String baseUri;

    private ObjectMapper mapper;

    @Setup
    public void setup() throws Exception {
        // jersey
        ResourceConfig jerseyConfig = new ResourceConfig()
                .property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true)
                .property(ServerProperties.WADL_FEATURE_DISABLE, true)
                .register(JacksonFeature.withoutExceptionMappers())
                .register(new ObjectMapperProvider(new ObjectMapper()))
                .register(new BenchmarkResource());

        // servlet initialization
        ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath("/")
                .setDeploymentName("benchmark")
                .addServlet(new ServletInfo(
                        "servlet",
                        org.glassfish.jersey.servlet.ServletContainer.class,
                        new ImmediateInstanceFactory<>(new org.glassfish.jersey.servlet.ServletContainer(jerseyConfig)))
                        .addMapping("/*"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        HttpHandler handler = manager.start();

        // compression
        ObjectPool<Deflater> pool = DeflatingStreamSinkConduit.simpleDeflaterPool(100, Deflater.BEST_SPEED);
        handler = new EncodingHandler(handler, new ContentEncodingRepository()
                .addEncodingHandler("gzip", new GzipEncodingProvider(pool), 50));

        // server
        server = Undertow.builder()
                .setIoThreads(14)
                .setWorkerThreads(64)
                .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
                .setHandler(handler).addHttpsListener(0, "0.0.0.0", TLSUtils.newServerContext()).build();
        server.start();

        // client
        client = HttpClients.custom()
                .disableConnectionState()
                .disableAutomaticRetries()
                .setSSLContext(TLSUtils.newClientContext())
                .setMaxConnPerRoute(100)
                .setMaxConnTotal(100)
                .build();

        int port = ((InetSocketAddress) server.getListenerInfo().iterator().next().getAddress()).getPort();
        baseUri =  "https://localhost:" + port;
        mapper = new ObjectMapper();
    }

    @TearDown
    public void tearDown() throws Exception {
        server.stop();
        client.close();
        baseUri = null;
        mapper = null;
    }

    @Benchmark
    public List<Integer> benchmarkBlockingEmptyPost() throws IOException {
        HttpGet request = new HttpGet(baseUri + "/getIntegerList");
        request.addHeader("Accept", "application/json");
        try (CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200) {
                throw new RuntimeException("Unexpected status: " + status);
            }
            try (InputStream content = response.getEntity().getContent()) {
                return mapper.readValue(content, new TypeReference<List<Integer>>(){});
            }

        }
    }

    @Provider
    public static final class ObjectMapperProvider implements ContextResolver<ObjectMapper> {

        private final ObjectMapper mapper;

        public ObjectMapperProvider(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ObjectMapper getContext(Class<?> _type) {
            return mapper;
        }
    }

    @Path("")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public static final class BenchmarkResource {
        private static final List<Integer> RESPONSE =
                IntStream.rangeClosed(0, 3200).boxed().collect(Collectors.toList());

        @GET
        @Path("getIntegerList")
        public List<Integer> getIntegerList() {
            return RESPONSE;
        }
    }

    public static void main(String[] args) throws Exception {
        new Runner(new OptionsBuilder()
                .include(JerseyBenchmarks.class.getName())
                .build())
                .run();
    }
}
