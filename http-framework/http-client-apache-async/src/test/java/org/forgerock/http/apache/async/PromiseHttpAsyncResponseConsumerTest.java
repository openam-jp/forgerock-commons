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
package org.forgerock.http.apache.async;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.apache.async.CloseableBufferFactory.closeableByteBufferFactory;
import static org.forgerock.http.io.IO.newTemporaryStorage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.PromiseImpl;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PromiseHttpAsyncResponseConsumerTest {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @AfterClass
    public void afterClass() throws InterruptedException {
        executorService.shutdown();
        if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
            throw new RuntimeException("Unable to terminate the ExecutorService");
        }
    }


    @DataProvider
    public static Object[][] invalidResponses() {
        return new Object[][] {
                // Empty response : that will fail immediately without having received a response
                { "" },
                // Give a Content-Length greater than the actual entity length : that will fail as it expects
                // to read more bytes
                { "HTTP/1.1 200 OK\nContent-Length: 42\n\nForgeRock" }
        };
    }

    @Test(dataProvider = "invalidResponses")
    public void shouldGetBadGatewayStatus(String rawResponse) throws Exception {
        try (BasicHttpServer httpServer = new BasicHttpServer(executorService, rawResponse);
                CloseableHttpAsyncClient client = HttpAsyncClients.createMinimal()) {
            int port = httpServer.start();
            URI uri = new URI(format("http://localhost:%d/foo", port));
            final PromiseImpl<Response, NeverThrowsException> promise = PromiseImpl.create();
            AsyncHttpClient.PromiseHttpAsyncResponseConsumer responseConsumer =
                    new AsyncHttpClient.PromiseHttpAsyncResponseConsumer(promise, uri.toASCIIString(),
                            newTemporaryStorage(), closeableByteBufferFactory(2, 256));

            client.start();
            client.execute(requestProducer(uri), responseConsumer, null);

            // Verify the response (block until reception)
            Response response = promise.get();
            assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);
        }
    }

    private HttpAsyncRequestProducer requestProducer(URI uri) {
        return HttpAsyncMethods.create(new HttpGet(uri));
    }

    /**
     * Minimal implementation of HTTP server that just outputs the given raw response.
     * That allows to answer some incorrect responses and thus to throw some Exceptions on the client side.
     * It only accepts one connection then it stops.
     */
    private static class BasicHttpServer implements AutoCloseable {

        private final ExecutorService executorService;
        private final String rawResponse;

        private ServerSocket serverSocket;

        BasicHttpServer(ExecutorService executorService, String rawResponse) {
            this.executorService = executorService;
            this.rawResponse = rawResponse;
        }

        int start() throws IOException {
            serverSocket = new ServerSocket(0);
            Runnable httpServer = new Runnable() {
                @Override
                public void run() {
                    try (Socket connectionSocket = serverSocket.accept();
                         DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream())) {
                        outToClient.write(rawResponse.getBytes(StandardCharsets.US_ASCII));
                    } catch (IOException e) {
                        // To get some traces in case of failure on CI.
                        e.printStackTrace();
                    }
                }
            };
            this.executorService.submit(httpServer);
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }

    }

}
