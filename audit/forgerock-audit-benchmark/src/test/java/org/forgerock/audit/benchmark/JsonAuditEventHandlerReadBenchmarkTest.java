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

import static org.forgerock.audit.AuditServiceProxy.ACTION_PARAM_TARGET_HANDLER;
import static org.forgerock.audit.handlers.json.JsonAuditEventHandler.FLUSH_FILE_ACTION_NAME;
import static org.forgerock.json.JsonValue.*;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.forgerock.audit.handlers.json.JsonAuditEventHandler;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.IdentifierQueryResourceHandler;
import org.forgerock.json.resource.QueryFilters;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.Requests;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * Read-throughput benchmarks for {@link JsonAuditEventHandler}.
 */
public class JsonAuditEventHandlerReadBenchmarkTest extends BenchmarkBase {

    private static final int PRE_POPULATED_EVENT_COUNT = 100_000;
    private static final String ACCESS = "access";

    @State(Scope.Benchmark)
    public static class ReadState extends JsonAuditEventHandlerWriteBenchmarkTest.DefaultState  {
        private int counter;
        private String[] identifiers;

        /**
         * Gets a pseudo-random audit event identifier.
         *
         * @return identifier
         */
        String getRandomIdentifier() {
            // NOTE: it doesn't matter that the counter will be updated by multiple thread concurrently
            return identifiers[++counter % PRE_POPULATED_EVENT_COUNT];
        }

        @Override
        protected void afterStartup() throws Exception {
            // pre-populate with data and store generated IDs
            identifiers = new String[PRE_POPULATED_EVENT_COUNT];
            for (int i = 0; i < PRE_POPULATED_EVENT_COUNT; ++i) {
                final String id = String.format("%010d", i);
                final JsonValue event = json(object(
                        field("_id", id), field("timestamp", id), field("transactionId", id)));
                identifiers[i] = handler.publishEvent(null, ACCESS, event).get().getId();
            }

            // flush the underlying file buffer, after all test-events have been published
            final ActionRequest actionRequest = Requests.newActionRequest(ACCESS, FLUSH_FILE_ACTION_NAME)
                    .setAdditionalParameter(ACTION_PARAM_TARGET_HANDLER, "json");
            handler.handleAction(null, ACCESS, actionRequest).getOrThrow();

            // shuffle ordering of IDs, to simulate random access
            // https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle#The_modern_algorithm
            final Random random = ThreadLocalRandom.current();
            for (int i = PRE_POPULATED_EVENT_COUNT - 1; i > 0; --i) {
                final int index = random.nextInt(i + 1);
                final String s = identifiers[index];
                identifiers[index] = identifiers[i];
                identifiers[i] = s;
            }

            // sleep to make sure async-publisher-thread finished all work
            Thread.sleep(1_000);
        }
    }

    @Benchmark
    public String readById(final ReadState state) throws Exception {
        return state.handler.readEvent(null, ACCESS, state.getRandomIdentifier()).getOrThrow().getId();
    }

    @Benchmark
    public QueryResponse queryForId(final ReadState state) throws Exception {
        final IdentifierQueryResourceHandler queryHandler = new IdentifierQueryResourceHandler(
                state.getRandomIdentifier());
        final QueryRequest queryRequest = Requests.newQueryRequest(ACCESS)
                .setQueryFilter(QueryFilters.parse("/_id eq \"" + queryHandler.getId() + "\""));
        return state.handler.queryEvents(null, ACCESS, queryRequest, queryHandler).getOrThrow();
    }
}
