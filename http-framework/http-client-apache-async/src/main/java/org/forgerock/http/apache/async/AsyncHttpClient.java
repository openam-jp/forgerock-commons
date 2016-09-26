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

import static java.nio.channels.Channels.newChannel;
import static org.forgerock.http.apache.async.CloseableBufferFactory.closeableByteBufferFactory;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.forgerock.http.apache.AbstractHttpClient;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.io.PipeBufferedStream;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.util.Factory;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Apache HTTP Async Client based implementation.
 */
public class AsyncHttpClient extends AbstractHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(AsyncHttpClient.class);

    private final CloseableHttpAsyncClient client;
    private final Factory<Buffer> storage;
    private final CloseableBufferFactory<ByteBuffer> bufferFactory;

    AsyncHttpClient(final CloseableHttpAsyncClient client, final Factory<Buffer> storage, final int threadCount) {
        // Client should already be started
        this.client = client;
        this.storage = storage;
        this.bufferFactory = closeableByteBufferFactory(threadCount, 8 * 1_024);
    }

    @Override
    public Promise<Response, NeverThrowsException> sendAsync(final Request request) {

        HttpUriRequest clientRequest = createHttpUriRequest(request);

        // Send request and return the configured Promise
        final PromiseImpl<Response, NeverThrowsException> promise = PromiseImpl.create();

        HttpAsyncResponseConsumer<HttpResponse> httpAsyncResponseConsumer =
                new PromiseHttpAsyncResponseConsumer(promise, request.getUri().asURI().toASCIIString(), storage,
                        bufferFactory);

        // Copy the MDC before submitting the job
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc != null) {
            httpAsyncResponseConsumer = new MdcAwareHttpAsyncResponseConsumer(httpAsyncResponseConsumer, mdc);
        }

        // Execute
        client.execute(HttpAsyncMethods.create(clientRequest), httpAsyncResponseConsumer, null);

        return promise;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    static final class PromiseHttpAsyncResponseConsumer implements HttpAsyncResponseConsumer<HttpResponse> {

        private final PromiseImpl<Response, NeverThrowsException> promise;

        private final Factory<Buffer> storage;
        private final String uri;
        private final CloseableBufferFactory<ByteBuffer> bufferFactory;

        private Response response;
        private WritableByteChannel channel;
        private HttpResponse result;
        private Exception exception;

        PromiseHttpAsyncResponseConsumer(PromiseImpl<Response, NeverThrowsException> promise, String uri,
                Factory<Buffer> storage, CloseableBufferFactory<ByteBuffer> bufferFactory) {
            this.promise = promise;
            this.storage = storage;
            this.uri = uri;
            this.bufferFactory = bufferFactory;
        }

        @Override
        public void responseReceived(HttpResponse httpResponse) throws IOException, HttpException {
            result = httpResponse;
            response = createResponseWithoutEntity(httpResponse);

            HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                PipeBufferedStream pipe = new PipeBufferedStream(storage);
                channel = newChannel(pipe.getIn());
                response.getEntity().setRawContentInputStream(pipe.getOut());
            }
        }

        @Override
        public void consumeContent(ContentDecoder contentDecoder, IOControl ioControl) throws IOException {
            try (CloseableBufferFactory<ByteBuffer>.CloseableBuffer buffer = bufferFactory.newInstance()) {
                ByteBuffer byteBuffer = buffer.getBuffer();
                while (contentDecoder.read(byteBuffer) > 0) {
                    byteBuffer.flip();
                    channel.write(byteBuffer);
                    byteBuffer.clear();
                }
            }

            if (contentDecoder.isCompleted()) {
                channel.close();
            }
        }

        @Override
        public void responseCompleted(HttpContext httpContext) {
            promise.handleResult(response);
        }

        @Override
        public void failed(Exception e) {
            closeSilently(response, channel);
            exception = e;
            logger.trace("Failed to obtain response for {}", uri, e);
            promise.handleResult(new Response(Status.BAD_GATEWAY).setCause(e));
        }

        @Override
        public Exception getException() {
            return exception;
        }

        @Override
        public HttpResponse getResult() {
            return result;
        }

        @Override
        public boolean isDone() {
            return promise.isDone();
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public boolean cancel() {
            return false;
        }

    }

    /**
     * This HttpAsyncResponseConsumer setup the MDC when the async HTTP client hand-off response processing back to the
     * caller. In other words, all log statements appearing before this consumer is invoked will not have updated
     * contextual information.
     */
    private static final class MdcAwareHttpAsyncResponseConsumer implements HttpAsyncResponseConsumer<HttpResponse> {

        private final HttpAsyncResponseConsumer<HttpResponse> delegate;

        private final Map<String, String> mdc;
        MdcAwareHttpAsyncResponseConsumer(HttpAsyncResponseConsumer<HttpResponse> delegate, Map<String, String> mdc) {
            this.delegate = delegate;
            this.mdc = mdc;
        }

        @Override
        public void responseReceived(HttpResponse response) throws IOException, HttpException {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                delegate.responseReceived(response);
            } finally {
                restoreMdc(previous);
            }
        }

        @Override
        public void consumeContent(ContentDecoder decoder, IOControl ioctrl) throws IOException {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                delegate.consumeContent(decoder, ioctrl);
            } finally {
                restoreMdc(previous);
            }
        }

        @Override
        public void responseCompleted(HttpContext context) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                delegate.responseCompleted(context);
            } finally {
                restoreMdc(previous);
            }
        }

        @Override
        public void failed(Exception ex) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                delegate.failed(ex);
            } finally {
                restoreMdc(previous);
            }
        }

        @Override
        public Exception getException() {
            return delegate.getException();
        }

        @Override
        public HttpResponse getResult() {
            return delegate.getResult();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public void close() throws IOException {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                delegate.close();
            } finally {
                restoreMdc(previous);
            }
        }

        @Override
        public boolean cancel() {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.setContextMap(mdc);
                return delegate.cancel();
            } finally {
                restoreMdc(previous);
            }
        }

        private void restoreMdc(Map<String, String> previous) {
            if (previous != null) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }
}
