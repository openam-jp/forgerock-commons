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

package org.forgerock.http.handler;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.composite;
import static com.xebialabs.restito.semantics.Action.ok;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.not;
import static com.xebialabs.restito.semantics.Condition.post;
import static com.xebialabs.restito.semantics.Condition.withPostBody;
import static com.xebialabs.restito.semantics.Condition.withPostBodyContaining;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.RootContext;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.xebialabs.restito.server.StubServer;

/**
 * This is the base class to ensure common behaviour between CHF client implementations.
 */
public abstract class HttpClientHandlerTest {

    /** The HTTP server to query. */
    protected StubServer server;

    /**
     * Setup the HTTP server.
     */
    @BeforeTest
    public void setUp() {
        // Create mock HTTP server.
        server = new StubServer().run();
    }

    /**
     * Stop the HTTP server.
     */
    @AfterTest
    public void tearDown() {
        server.stop();
    }

    /**
     * Reset the HTTP server.
     */
    @BeforeMethod
    public void cleanup() {
        // Clear mocked invocations between tests
        // So we can reuse the server instance (less traces) still having isolation
        if (server != null) {
            server.clear();
        }
    }

    /**
     * Ensure that a response is produced.
     * @throws Exception In case of failure.
     */
    @Test
    public void shouldProduceResponse() throws Exception {
        whenHttp(server).match(post("/ping"))
                .then(composite(ok(), stringContent("Pong")));

        try (HttpClientHandler handler = buildHttpClientHandler()) {
            Request request = new Request();
            request.setMethod("POST");
            request.setUri(format("http://localhost:%d/ping", server.getPort()));
            Response response = handler.handle(new RootContext(), request).get();
            assertThat(response.getStatus()).isEqualTo(Status.OK);
            assertThat(response.getEntity().getString()).isEqualTo("Pong");
        }
    }

    /**
     * Ensure that a request with a posted entity can be sent.
     * @throws Exception In case of failure.
     */
    @Test
    public void shouldSendPostHttpMessageWithEntityContent() throws Exception {
        whenHttp(server).match(post("/test"),
                withPostBodyContaining("Hello"))
                .then(status(HttpStatus.OK_200));

        try (HttpClientHandler handler = buildHttpClientHandler()) {
            Request request = new Request();
            request.setMethod("POST");
            request.setUri(format("http://localhost:%d/test", server.getPort()));
            request.getEntity().setString("Hello");
            assertThat(handler.handle(new RootContext(), request).get().getStatus()).isEqualTo(Status.OK);
        }
    }

    /**
     * Ensure that a request with a posted empty entity can be sent.
     * @throws Exception In case of failure.
     */
    @Test
    public void shouldSendPostHttpMessageWithEmptyEntity() throws Exception {
        whenHttp(server).match(post("/test"),
                not(withPostBody()))
                .then(status(HttpStatus.OK_200));

        try (HttpClientHandler handler = buildHttpClientHandler()) {
            Request request = new Request();
            request.setMethod("POST");
            request.setUri(format("http://localhost:%d/test", server.getPort()));
            assertThat(handler.handle(new RootContext(), request).get().getStatus()).isEqualTo(Status.OK);
        }
    }

    /**
     * Ensure that a response with status BAD_GATEWAY is received when an error occurred.
     * @throws Exception In case of failure.
     */
    @Test
    public void shouldFailToObtainResponse() throws Exception {
        // The request is invalid because we did not specify the method.
        final Request invalidRequest = new Request();
        invalidRequest.setUri(format("http://localhost:%d/shouldFail", server.getPort()));

        try (HttpClientHandler handler = buildHttpClientHandler()) {
            final Response response = handler.handle(new RootContext(), invalidRequest).get();

            assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);
            assertThat(response.getEntity().getString()).isEmpty();
            assertThat(response.getCause()).isNotNull();
        }
    }

    /**
     *  Instantiates the expected HttpClientHandler to test.
     *  @return the expected HttpClientHandler to test.
     *  @throws HttpApplicationException In case of failure when trying to create it.
     */
    protected abstract HttpClientHandler buildHttpClientHandler() throws HttpApplicationException;

}
