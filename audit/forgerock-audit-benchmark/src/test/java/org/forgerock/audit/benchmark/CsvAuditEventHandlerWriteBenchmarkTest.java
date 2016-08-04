/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.audit.benchmark;

import static org.forgerock.json.JsonValue.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.forgerock.audit.events.handlers.AuditEventHandler;
import org.forgerock.audit.handlers.csv.CsvAuditEventHandler;
import org.forgerock.audit.handlers.csv.CsvAuditEventHandlerConfiguration;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ResourceResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Write-throughput benchmarks for {@link CsvAuditEventHandler}.
 */
public class CsvAuditEventHandlerWriteBenchmarkTest extends BenchmarkBase {

    /**
     * Maximum log file size, before file-rotation is triggered.
     */
    static final long MAX_FILE_SIZE = 100_000_000;

    private static final String KEYSTORE_FILENAME = "target/test-classes/keystore-signature.jks";
    private static final String KEYSTORE_PASSWORD = "password";
    private static final String SIGNATURE_INTERVAL = "10 seconds";
    private static final String ACCESS = "access";
    private static final String ACTIVITY = "activity";
    private static final Set<String> TOPICS_SET = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new String[]{ACCESS, ACTIVITY})));

    static class DefaultState extends AuditEventHandlerBenchmarkState<CsvAuditEventHandlerConfiguration> {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public CsvAuditEventHandlerConfiguration buildBaseConfiguration() {
            final CsvAuditEventHandlerConfiguration configuration = new CsvAuditEventHandlerConfiguration();
            configuration.setName("csv");
            configuration.setEnabled(true);
            configuration.setLogDirectory(getLogDirectory());
            configuration.setTopics(CsvAuditEventHandlerWriteBenchmarkTest.TOPICS_SET);
            return configuration;
        }

        @Override
        public AuditEventHandler buildAuditEventHandler(final CsvAuditEventHandlerConfiguration configuration)
            throws Exception {
            return new CsvAuditEventHandler(configuration, getEventTopicsMetaData("/events.json"), null);
        }

        /**
         * Builds a simple, unique event instance.
         *
         * @return Event instance
         */
        protected JsonValue buildUniqueEvent() {
            final String simpleId = Long.toString(counter.getAndIncrement());
            return json(object(field("_id", simpleId), field("timestamp", simpleId), field("transactionId", simpleId)));
        }
    }

    @State(Scope.Benchmark)
    public static class UnbufferedWriteState extends DefaultState {
        // empty
    }

    @Benchmark
    public ResourceResponse unbufferedWrite(final UnbufferedWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class UnbufferedSecureWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getSecurity().setEnabled(true);
            configuration.getSecurity().setSignatureInterval(SIGNATURE_INTERVAL);
            configuration.getSecurity().setFilename(KEYSTORE_FILENAME);
            configuration.getSecurity().setPassword(KEYSTORE_PASSWORD);
        }
    }

    @Benchmark
    public ResourceResponse unbufferedSecureWrite(final UnbufferedSecureWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class UnbufferedRotatedWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getFileRotation().setRotationEnabled(true);
            configuration.getFileRotation().setMaxFileSize(MAX_FILE_SIZE);
        }
    }

    @Benchmark
    public ResourceResponse unbufferedRotatedWrite(final UnbufferedRotatedWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class BufferedWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getBuffering().setEnabled(true);
        }
    }

    @Benchmark
    public ResourceResponse bufferedWrite(final BufferedWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class BufferedFlushedWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getBuffering().setEnabled(true);
            configuration.getBuffering().setAutoFlush(true);
        }
    }

    @Benchmark
    public ResourceResponse bufferedFlushedWrite(final BufferedFlushedWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class BufferedRotatedWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getBuffering().setEnabled(true);
            configuration.getFileRotation().setRotationEnabled(true);
            configuration.getFileRotation().setMaxFileSize(MAX_FILE_SIZE);
        }
    }

    @Benchmark
    public ResourceResponse bufferedRotatedWrite(final BufferedRotatedWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class BufferedFlushedRotatedWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getBuffering().setEnabled(true);
            configuration.getBuffering().setAutoFlush(true);
            configuration.getFileRotation().setRotationEnabled(true);
            configuration.getFileRotation().setMaxFileSize(MAX_FILE_SIZE);
        }
    }

    @Benchmark
    public ResourceResponse bufferedFlushedRotatedWrite(final BufferedFlushedRotatedWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class BufferedSecureWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getBuffering().setEnabled(true);
            configuration.getSecurity().setEnabled(true);
            configuration.getSecurity().setSignatureInterval(SIGNATURE_INTERVAL);
            configuration.getSecurity().setFilename(KEYSTORE_FILENAME);
            configuration.getSecurity().setPassword(KEYSTORE_PASSWORD);
        }
    }

    @Benchmark
    public ResourceResponse bufferedSecureWrite(final BufferedSecureWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

    @State(Scope.Benchmark)
    public static class BufferedFlushedSecureWriteState extends DefaultState {
        @Override
        public void updateConfiguration(final CsvAuditEventHandlerConfiguration configuration) {
            configuration.getBuffering().setEnabled(true);
            configuration.getBuffering().setAutoFlush(true);
            configuration.getSecurity().setEnabled(true);
            configuration.getSecurity().setSignatureInterval(SIGNATURE_INTERVAL);
            configuration.getSecurity().setFilename(KEYSTORE_FILENAME);
            configuration.getSecurity().setPassword(KEYSTORE_PASSWORD);
        }
    }

    @Benchmark
    public ResourceResponse bufferedFlushedSecureWrite(final BufferedFlushedSecureWriteState state) throws Exception {
        return state.handler.publishEvent(null, ACCESS, state.buildUniqueEvent()).getOrThrow();
    }

}
