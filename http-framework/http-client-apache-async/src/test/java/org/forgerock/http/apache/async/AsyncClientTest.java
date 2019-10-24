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
 * Portions copyright 2019 Open Source Solution Technology Corporation
 */

package org.forgerock.http.apache.async;

import static com.xebialabs.restito.builder.stub.StubHttp.*;
import static com.xebialabs.restito.semantics.Action.composite;
import static com.xebialabs.restito.semantics.Action.*;
import static com.xebialabs.restito.semantics.Condition.*;
import static java.lang.String.*;
import static org.assertj.core.api.Assertions.*;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.handler.HttpClientHandler.HostnameVerifier;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.util.Options;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.xebialabs.restito.semantics.Applicable;
import com.xebialabs.restito.server.StubServer;

@SuppressWarnings("javadoc")
public class AsyncClientTest {

    private StubServer server;
    private StubServer httpsServer;
    private TrustManager[] trustManagers = { new TrustAll() };

    @BeforeTest
    public void setUp() throws Exception {
        // Create mock HTTP server.
        server = new StubServer().run();
        httpsServer = new StubServer().secured().run();
    }

    @AfterTest
    public void tearDown() throws Exception {
        server.stop();
        httpsServer.stop();
    }

    @BeforeMethod
    public void cleanup() throws Exception {
        // Clear mocked invocations between tests
        // So we can reuse the server instance (less traces) still having isolation
        if (server != null) {
            server.clearCalls();
            server.clearStubs();
        }
        if (httpsServer != null) {
            httpsServer.clearCalls();
            httpsServer.clearStubs();
        }
    }

    @Test
    public void shouldProducesResponse() throws Exception {
        whenHttp(server).match(post("/ping"))
                        .then(composite(ok(), stringContent("Pong")));

        Client client = new Client(new HttpClientHandler());
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(format("http://localhost:%d/ping", server.getPort()));
        Response response = client.send(request).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getEntity().getString()).isEqualTo("Pong");
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

        Client client = new Client(new HttpClientHandler());
        Promise<Response, NeverThrowsException> promise = client.send(request);

        // We're still waiting for the server's response
        assertThat(promise.isDone()).isFalse();

        // Unlock server
        one.countDown();

        // Verify the response (block until reception)
        assertThat(promise.get().getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void shouldFailToObtainResponse() throws Exception {
        final Client client = new Client(new HttpClientHandler());
        final Request invalidRequest = new Request();
        invalidRequest.setUri(format("http://localhost:%d/shouldFail", server.getPort()));
        final Response response = client.send(invalidRequest).get();

        assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);
        assertThat(response.getEntity().getString()).isEmpty();
        assertThat(response.getCause()).isNotNull();
    }
    
    @Test
    public void shouldVerifyTlsHostNameDefault() throws Exception {
        Options options = Options.defaultOptions()
                .set(HttpClientHandler.OPTION_TRUST_MANAGERS, trustManagers);
        Client client = new Client(new HttpClientHandler(options));
        Request request = new Request();
        request.setMethod("POST");
        // Use IP address to fail host name verification.
        request.setUri(format("https://127.0.0.1:%d/ping", httpsServer.getPort()));
        Response response = client.send(request).get();
        assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);
    }

    @Test
    public void shouldVerifyTlsHostName() throws Exception {
        Options options = Options.defaultOptions()
                .set(HttpClientHandler.OPTION_HOSTNAME_VERIFIER, HostnameVerifier.STRICT)
                .set(HttpClientHandler.OPTION_TRUST_MANAGERS, trustManagers);
        Client client = new Client(new HttpClientHandler(options));
        Request request = new Request();
        request.setMethod("POST");
        // Use IP address to fail host name verification.
        request.setUri(format("https://127.0.0.1:%d/ping", httpsServer.getPort()));
        Response response = client.send(request).get();
        assertThat(response.getStatus()).isEqualTo(Status.BAD_GATEWAY);
    }

    @Test
    public void shouldIgnoreTlsHostName() throws Exception {
        whenHttp(httpsServer).match(post("/ping"))
                .then(composite(ok(), stringContent("Pong")));
        
        Options options = Options.defaultOptions()
                .set(HttpClientHandler.OPTION_HOSTNAME_VERIFIER, HostnameVerifier.ALLOW_ALL)
                .set(HttpClientHandler.OPTION_TRUST_MANAGERS, trustManagers);
        Client client = new Client(new HttpClientHandler(options));
        Request request = new Request();
        request.setMethod("POST");
        // Use IP address to fail host name verification.
        request.setUri(format("https://127.0.0.1:%d/ping", httpsServer.getPort()));
        Response response = client.send(request).get();
        assertThat(response.getStatus()).isEqualTo(Status.OK);
        assertThat(response.getEntity().getString()).isEqualTo("Pong");
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
    
    /**
     * TrustManager to allow all certificates.
     */
    private class TrustAll implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1) 
                throws CertificateException {}

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1) 
                throws CertificateException {}

        @Override
        public X509Certificate[] getAcceptedIssuers() { return null; }
    }

}
