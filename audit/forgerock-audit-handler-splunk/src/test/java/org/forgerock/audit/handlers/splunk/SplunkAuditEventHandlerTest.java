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
package org.forgerock.audit.handlers.splunk;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.util.time.Duration.duration;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.forgerock.audit.events.EventTopicsMetaData;
import org.forgerock.audit.events.handlers.buffering.BatchException;
import org.forgerock.audit.events.handlers.buffering.BatchPublisher;
import org.forgerock.audit.events.handlers.buffering.BatchPublisherFactory;
import org.forgerock.audit.events.handlers.buffering.BufferedBatchPublisher;
import org.forgerock.audit.handlers.splunk.SplunkAuditEventHandlerConfiguration.BufferingConfiguration;
import org.forgerock.audit.handlers.splunk.SplunkAuditEventHandlerConfiguration.ConnectionConfiguration;
import org.forgerock.http.Client;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.ServiceUnavailableException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit test to exercise the {@link SplunkAuditEventHandler}.
 */
public final class SplunkAuditEventHandlerTest {

    private SplunkAuditEventHandler splunkHandler;
    private BatchPublisherFactory publisherFactory;
    private BatchPublisher publisher;
    private BufferedBatchPublisher.Builder publisherBuilder;
    private Handler handler;

    @BeforeMethod
    public void setUp() {
        BufferingConfiguration bufferingConfiguration = new BufferingConfiguration();
        bufferingConfiguration.setMaxBatchedEvents(5);
        bufferingConfiguration.setMaxSize(1024);
        bufferingConfiguration.setWriteInterval("500 milliseconds");

        ConnectionConfiguration connectionConfiguration = new ConnectionConfiguration();
        connectionConfiguration.setPort(8088);
        connectionConfiguration.setHost("localhost");
        connectionConfiguration.setUseSSL(false);

        SplunkAuditEventHandlerConfiguration configuration = new SplunkAuditEventHandlerConfiguration();
        configuration.setAuthzToken("abc-def-ghi");
        configuration.setName("test");
        configuration.setTopics(singleton("access"));
        configuration.setBuffering(bufferingConfiguration);
        configuration.setConnection(connectionConfiguration);

        EventTopicsMetaData topicsMetaData = new EventTopicsMetaData(Collections.<String, JsonValue>emptyMap());

        publisherBuilder = mock(BufferedBatchPublisher.Builder.class);
        when(publisherBuilder.capacity(1024)).thenReturn(publisherBuilder);
        when(publisherBuilder.writeInterval(duration(500, TimeUnit.MILLISECONDS))).thenReturn(publisherBuilder);
        when(publisherBuilder.maxBatchEvents(5)).thenReturn(publisherBuilder);
        when(publisherBuilder.averagePerEventPayloadSize(1280)).thenReturn(publisherBuilder);
        when(publisherBuilder.autoFlush(true)).thenReturn(publisherBuilder);
        publisher = mock(BatchPublisher.class);
        when(publisherBuilder.build()).thenReturn(publisher);

        publisherFactory = mock(BatchPublisherFactory.class);
        when(publisherFactory.newBufferedPublisher(isA(SplunkAuditEventHandler.class))).thenReturn(publisherBuilder);

        handler = mock(Handler.class);
        Client client = new Client(handler);
        splunkHandler = new SplunkAuditEventHandler(configuration, topicsMetaData, publisherFactory, client);
    }

    @Test
    public void startupInitialisesTheBatchPublisher() throws ResourceException {
        // When
        splunkHandler.startup();

        // Then
        verify(publisher).startup();
    }

    @Test
    public void shutdownAlsoCallsTheBatchPublisherToShutdown() throws ResourceException {
        // When
        splunkHandler.shutdown();

        // Then
        verify(publisher).shutdown();
    }

    @Test
    public void publishedEventsGetOfferedToTheBatchPublisher() throws ResourceException {
        // Given
        Context context = mock(Context.class);
        JsonValue event = json(object(field("_id", "12345")));
        when(publisher.offer("access", event)).thenReturn(true);

        // When
        Promise<ResourceResponse, ResourceException> result = splunkHandler.publishEvent(context, "access", event);

        // Then
        verify(publisher).offer("access", event);
        ResourceResponse response = result.getOrThrowUninterruptibly();
        assertThat(response.getId()).isEqualTo("12345");
    }

    @Test(expectedExceptions = ServiceUnavailableException.class, expectedExceptionsMessageRegExp = "^.*access/12345$")
    public void failureOfTheBatchPublisherToConsumeEventResultsInException() throws ResourceException {
        // Given
        Context context = mock(Context.class);
        JsonValue event = json(object(field("_id", "12345")));
        when(publisher.offer("access", event)).thenReturn(false);

        // When
        Promise<ResourceResponse, ResourceException> result = splunkHandler.publishEvent(context, "access", event);

        // Then
        verify(publisher).offer("access", event);
        result.getOrThrowUninterruptibly();
    }

    @Test
    public void eventsSuccessfullyBatchedUp() throws BatchException {
        // Given
        JsonValue event = json(object(field("_id", "123")));
        StringBuilder payload = new StringBuilder("previousEvents\n");

        // When
        splunkHandler.addToBatch("access", event, payload);

        // Then
        assertThat(payload)
                .containsPattern("^previousEvents\n.*\"_id\"\\s*:\\s*\"123\".*\"_topic\"\\s*:\\s*\"access\".*$");
    }

    @Test
    public void batchPayloadSuccessfullyPostedToEndpoint() throws IOException, BatchException {
        // Given
        Response response = new Response(Status.OK);
        Promise<Response, NeverThrowsException> responsePromise = Response.newResponsePromise(response);
        when(handler.handle(isA(Context.class), isA(Request.class))).thenReturn(responsePromise);

        // When
        Promise<Void, BatchException> promiseResponse = splunkHandler.publishBatch("{ \"_id\": \"123\" }");

        // Then
        promiseResponse.getOrThrowUninterruptibly();

        ArgumentCaptor<Request> requestCapture = ArgumentCaptor.forClass(Request.class);
        verify(handler).handle(isA(Context.class), requestCapture.capture());

        Request request = requestCapture.getValue();
        assertThat(request.getUri().toString()).isEqualTo("http://localhost:8088/services/collector/raw");
        assertThat(request.getEntity().getString()).isEqualTo("{ \"_id\": \"123\" }");
        assertThat(request.getHeaders().get("Authorization").getFirstValue()).isEqualTo("Splunk abc-def-ghi");
        assertThat(request.getHeaders().get("X-Splunk-Request-Channel").getFirstValue()).isNotEmpty();
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    @Test(expectedExceptions = BatchException.class)
    public void batchExceptionThrownWhenPostedPayloadIsRejected() throws BatchException {
        // Given
        Response response = new Response(Status.INTERNAL_SERVER_ERROR);
        Promise<Response, NeverThrowsException> responsePromise = Response.newResponsePromise(response);
        when(handler.handle(isA(Context.class), isA(Request.class))).thenReturn(responsePromise);

        // When
        Promise<Void, BatchException> promiseResponse = splunkHandler.publishBatch("{ \"_id\": \"123\" }");

        // Then
        promiseResponse.getOrThrowUninterruptibly();
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void readIsNotCurrentlySupported() throws ResourceException {
        // When
        Promise<ResourceResponse, ResourceException> response = splunkHandler
                .readEvent(mock(Context.class), "access", "123");

        // Then
        response.getOrThrowUninterruptibly();
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void queryIsNotCurrentlySupported() throws ResourceException {
        // When
        Promise<QueryResponse, ResourceException> response = splunkHandler.queryEvents(mock(Context.class), "access",
                mock(QueryRequest.class), mock(QueryResourceHandler.class));

        // Then
        response.getOrThrowUninterruptibly();
    }

}