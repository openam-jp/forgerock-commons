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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.http.apache.async;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.composite;
import static com.xebialabs.restito.semantics.Action.ok;
import static com.xebialabs.restito.semantics.Condition.post;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.handler.HttpClientHandlerTest;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.http.spi.Loader;
import org.forgerock.services.context.RootContext;
import org.forgerock.util.Options;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.Test;

import com.xebialabs.restito.semantics.Applicable;

@SuppressWarnings("javadoc")
public class AsyncHttpClientTest extends HttpClientHandlerTest {

    @Override
    protected HttpClientHandler buildHttpClientHandler() throws HttpApplicationException {
        Loader customerLoader = new Loader() {
            @Override
            public <S> S load(Class<S> service, Options options) {
                try {
                    return (S) Class.forName("org.forgerock.http.apache.async.AsyncHttpClientProvider").newInstance();
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Options options = Options.defaultOptions().set(HttpClientHandler.OPTION_LOADER, customerLoader);
        return new HttpClientHandler(options);
    }

    /**
     * This test is performing a single request that is being blocked on the server side until a latch is released.
     * This ensure that, because the main thread is blocked, the processing is done in another thread.
     */
    @Test
    public void shouldDoTheProcessingAsynchronously() throws Exception {
        CountDownLatch one = new CountDownLatch(1);
        whenHttp(server).match(post("/ping"))
                        .then(composite(ok(), new WaitForLatch(one)));

        Request request = new Request();
        request.setMethod("POST");
        request.setUri(format("http://localhost:%d/ping", server.getPort()));

        try (HttpClientHandler handler = new HttpClientHandler()) {
            Promise<Response, NeverThrowsException> promise = handler.handle(new RootContext(), request);

            // We're still waiting for the server's response
            assertThat(promise.isDone()).isFalse();

            // Unlock server
            one.countDown();

            // Verify the response (block until reception)
            assertThat(promise.get().getStatus()).isEqualTo(Status.OK);
        }
    }

    private static class WaitForLatch implements Applicable {
        private final CountDownLatch one;
        public WaitForLatch(final CountDownLatch one) {
            this.one = one;
        }

        @Override
        public org.glassfish.grizzly.http.server.Response apply(final org.glassfish.grizzly.http.server.Response r) {
            try {
                one.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return r;
        }
    }
}
