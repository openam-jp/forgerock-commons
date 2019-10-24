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
 * Copyright 2015 ForgeRock AS.
 * Portions copyright 2019 Open Source Solution Technology Corporation
 */

package org.forgerock.http.apache.sync;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Condition.post;
import static com.xebialabs.restito.semantics.Condition.withPostBody;
import static com.xebialabs.restito.semantics.Condition.withPostBodyContaining;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.util.function.Predicate;
import com.xebialabs.restito.semantics.Predicates;
import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.handler.HttpClientHandler.HostnameVerifier;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Status;
import org.forgerock.util.Options;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.xebialabs.restito.semantics.Call;
import com.xebialabs.restito.semantics.Condition;
import com.xebialabs.restito.server.StubServer;

@SuppressWarnings("javadoc")
public class SyncClientTest {

    // TODO: can we test connection pooling, reuse, SSL, HTTP version, etc?

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
    public void shouldSendPostHttpMessageWithEntityContent() throws Exception {
        whenHttp(server).match(post("/test"),
                               withPostBodyContaining("Hello"))
                        .then(status(HttpStatus.OK_200));

        Client client = new Client(new HttpClientHandler());
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(format("http://localhost:%d/test", server.getPort()));
        request.getEntity().setString("Hello");
        assertThat(client.send(request).get().getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void shouldSendPostHttpMessageWithEmptyEntity() throws Exception {
        whenHttp(server).match(post("/test"),
                               not(withPostBody()))
                        .then(status(HttpStatus.OK_200));

        Client client = new Client(new HttpClientHandler());
        Request request = new Request();
        request.setMethod("POST");
        request.setUri(format("http://localhost:%d/test", server.getPort()));
        assertThat(client.send(request).get().getStatus()).isEqualTo(Status.OK);
    }

    @Test
    public void shouldVerifyTlsHostNameDefault() throws Exception {
        Options options = Options.defaultOptions()
                .set(HttpClientHandler.OPTION_TRUST_MANAGERS, trustManagers);
        Client client = new Client(new HttpClientHandler(options));
        Request request = new Request();
        request.setMethod("POST");
        // Use IP address to fail host name verification.
        request.setUri(format("https://127.0.0.1:%d/test", httpsServer.getPort()));
        assertThat(client.send(request).get().getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
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
        request.setUri(format("https://127.0.0.1:%d/test", httpsServer.getPort()));
        assertThat(client.send(request).get().getStatus()).isEqualTo(Status.INTERNAL_SERVER_ERROR);
    }
    
    @Test
    public void shouldIgnoreTlsHostName() throws Exception {
        whenHttp(httpsServer).match(post("/test"),
                               not(withPostBody()))
                        .then(status(HttpStatus.OK_200));

        Options options = Options.defaultOptions()
                .set(HttpClientHandler.OPTION_HOSTNAME_VERIFIER, HostnameVerifier.ALLOW_ALL)
                .set(HttpClientHandler.OPTION_TRUST_MANAGERS, trustManagers);
        Client client = new Client(new HttpClientHandler(options));
        Request request = new Request();
        request.setMethod("POST");
        // Use IP address to fail host name verification.
        request.setUri(format("https://127.0.0.1:%d/test", httpsServer.getPort()));
        assertThat(client.send(request).get().getStatus()).isEqualTo(Status.OK);
    }
    
    /**
     * Restito doesn't provide any way to express a negative condition yet.
     */
    private static Condition not(final Condition condition) {
        return new MyCondition(Predicates.not(condition.getPredicate()));
    }

    /**
     * And Condition has a unique protected constructor.
     */
    private static class MyCondition extends Condition {
        protected MyCondition(final Predicate<Call> predicate) {
            super(predicate);
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
