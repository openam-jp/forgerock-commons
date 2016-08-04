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

package org.forgerock.audit.handlers.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.audit.AuditServiceProxy.ACTION_PARAM_TARGET_HANDLER;
import static org.forgerock.audit.handlers.json.JsonAuditEventHandler.FLUSH_FILE_ACTION_NAME;
import static org.forgerock.audit.handlers.json.JsonAuditEventHandler.ROTATE_FILE_ACTION_NAME;
import static org.forgerock.json.JsonValue.*;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.util.test.FileUtils.deleteRecursively;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.forgerock.audit.events.EventTopicsMetaData;
import org.forgerock.audit.events.handlers.AuditEventHandler;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.IdentifierQueryResourceHandler;
import org.forgerock.json.resource.QueryFilters;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.Test;

public class JsonAuditEventHandlerTest {

    private static final int SMALL_EVENT_COUNT = 1_000;
    private static final int LARGE_EVENT_COUNT = 25_000;
    private static final int SLEEP_MILLIS = 250;
    private static final String ACCESS = "access";
    private static final String ACTIVITY = "activity";
    private static final Set<String> TOPICS_SET = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new String[]{ACCESS, ACTIVITY})));

    @Test
    public void testWriteAndReadEvents() throws Exception {
        final Path logDirectoryPath = Files.createTempDirectory(JsonAuditEventHandlerTest.class.getSimpleName());
        AuditEventHandler handler = null;
        try {
            final JsonAuditEventHandlerConfiguration configuration = buildConfiguration(SMALL_EVENT_COUNT,
                    logDirectoryPath);
            handler = new JsonAuditEventHandler(configuration, getEventTopicsMetaData("/events.json"));
            handler.startup();

            // pre-populate with data and store generated IDs
            final String[] identifiers = generateAndPublishEvents(SMALL_EVENT_COUNT, handler);

            // sleep to make sure async-publisher-thread finished all work
            Thread.sleep(SLEEP_MILLIS);

            for (final String resourceId : identifiers) {
                final Promise<ResourceResponse, ResourceException> response = handler.readEvent(null, ACCESS,
                        resourceId);
                assertThat(resourceId).isEqualTo(response.getOrThrow().getId());
            }
        } finally {
            try {
                if (handler != null) {
                    handler.shutdown();
                }
            } finally {
                deleteRecursively(logDirectoryPath);
            }
        }
    }

    @Test
    public void testWriteAndQueryEvents() throws Exception {
        // perform a normal query test
        writeAndQueryEvents(SMALL_EVENT_COUNT, false);
    }

    @Test
    public void testRotateDuringQuery() throws Exception {
        // force a rotate during a query, which causes the underlying file to be moved
        writeAndQueryEvents(LARGE_EVENT_COUNT, true);
    }

    private void writeAndQueryEvents(final int eventCount, final boolean forceRotateDuringQuery) throws Exception {
        final Path logDirectoryPath = Files.createTempDirectory(JsonAuditEventHandlerTest.class.getSimpleName());
        AuditEventHandler handler = null;
        try {
            final JsonAuditEventHandlerConfiguration configuration = buildConfiguration(eventCount, logDirectoryPath);
            if (forceRotateDuringQuery) {
                // enable rotation, but NOT any rotation policies, so that we can test the rotate-action
                configuration.getFileRotation().setRotationEnabled(true);
            }
            handler = new JsonAuditEventHandler(configuration, getEventTopicsMetaData("/events.json"));
            handler.startup();

            // pre-populate with data and store generated IDs
            String[] identifiers = generateAndPublishEvents(eventCount, handler);

            // sleep to make sure async-publisher-thread finished all work
            Thread.sleep(SLEEP_MILLIS);

            if (forceRotateDuringQuery) {
                // only search for the last entry, when testing rotation
                identifiers = new String[]{identifiers[identifiers.length - 1]};
            }

            for (final String resourceId : identifiers) {
                final IdentifierQueryResourceHandler queryHandler = new IdentifierQueryResourceHandler(
                        resourceId);
                final QueryRequest queryRequest = Requests.newQueryRequest(ACCESS)
                        .setQueryFilter(QueryFilters.parse("/_id eq \"" + queryHandler.getId() + "\""));

                if (forceRotateDuringQuery) {
                    // this rotate-action will NOT block the calling thread
                    final ActionRequest actionRequest = Requests.newActionRequest(ACCESS, ROTATE_FILE_ACTION_NAME)
                            .setAdditionalParameter(ACTION_PARAM_TARGET_HANDLER, "json");
                    handler.handleAction(null, ACCESS, actionRequest);
                }

                final QueryResponse queryResponse =
                        handler.queryEvents(null, ACCESS, queryRequest, queryHandler).getOrThrow();

                assertThat(queryResponse.getTotalPagedResults()).isEqualTo(1);
                assertThat(resourceId).isEqualTo(queryHandler.getResourceResponse().getId());
            }
        } finally {
            try {
                if (handler != null) {
                    handler.shutdown();
                }
                if (forceRotateDuringQuery) {
                    // a single additional rotation file should have been created
                    int logCount = 0;
                    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(logDirectoryPath)) {
                        for (Path path : directoryStream) {
                            if (path.toString().contains(ACCESS + '.' + JsonFileWriter.LOG_FILE_NAME_SUFFIX)) {
                                ++logCount;
                            }
                        }
                    }
                    assertThat(logCount).isEqualTo(2);
                }
            } finally {
                deleteRecursively(logDirectoryPath);
            }
        }
    }

    private String[] generateAndPublishEvents(final int eventCount, final AuditEventHandler handler) throws Exception {
        final String[] identifiers = new String[eventCount];
        for (int i = 0; i < identifiers.length; ++i) {
            final String id = String.format("%010d", i);
            final JsonValue event = json(object(
                    field("_id", id), field("timestamp", id), field("transactionId", id)));
            identifiers[i] = handler.publishEvent(null, ACCESS, event).get().getId();
        }

        // flush the underlying file buffer, after all test-events have been published
        final ActionRequest actionRequest = Requests.newActionRequest(ACCESS, FLUSH_FILE_ACTION_NAME)
                .setAdditionalParameter(ACTION_PARAM_TARGET_HANDLER, "json");
        handler.handleAction(null, ACCESS, actionRequest).getOrThrow();
        return identifiers;
    }

    private JsonAuditEventHandlerConfiguration buildConfiguration(final int eventCount, final Path logDirectoryPath) {
        final JsonAuditEventHandlerConfiguration configuration = new JsonAuditEventHandlerConfiguration();
        configuration.setName("json");
        configuration.setEnabled(true);
        configuration.setLogDirectory(logDirectoryPath.toAbsolutePath().toString());
        configuration.setTopics(TOPICS_SET);

        // leave space for the flush-file-buffer action's event
        configuration.getBuffering().setMaxSize(eventCount + 1);
        configuration.getBuffering().setWriteInterval("1 millis");
        return configuration;
    }

    /**
     * Loads {@link EventTopicsMetaData} from classpath resource.
     *
     * @param resourcePath Events JSON file path on classpath (should start with a forward-slash)
     * @return {@link EventTopicsMetaData} instance
     * @throws Exception when an error occurs
     */
    private EventTopicsMetaData getEventTopicsMetaData(final String resourcePath) throws Exception {
        Map<String, JsonValue> events = new LinkedHashMap<>();
        try (final InputStream configStream = getClass().getResourceAsStream(resourcePath)) {
            final JsonValue predefinedEventTypes = json(new ObjectMapper().readValue(configStream, Map.class));
            for (String eventTypeName : predefinedEventTypes.keys()) {
                events.put(eventTypeName, predefinedEventTypes.get(eventTypeName));
            }
        }
        return new EventTopicsMetaData(events);
    }
}
