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

package org.forgerock.http.apache.sync;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.handler.HttpClientHandlerTest;
import org.forgerock.http.spi.Loader;
import org.forgerock.util.Options;

@SuppressWarnings("javadoc")
public class SyncHttpClientTest extends HttpClientHandlerTest {

    // TODO: can we test connection pooling, reuse, SSL, HTTP version, etc?

    @Override
    protected HttpClientHandler buildHttpClientHandler() throws HttpApplicationException {
        Loader customerLoader = new Loader() {
            @Override
            public <S> S load(Class<S> service, Options options) {
                try {
                    return (S) Class.forName("org.forgerock.http.apache.sync.SyncHttpClientProvider").newInstance();
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Options options = Options.defaultOptions().set(HttpClientHandler.OPTION_LOADER, customerLoader);
        return new HttpClientHandler(options);
    }

}
