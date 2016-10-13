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

package org.forgerock.http.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.http.routing.UriRouterContext.uriRouterContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class UriRouterContextTest {

    @DataProvider
    private Object[][] testData() {
        Context parentContext = new RootContext();
        return new Object[][]{
            {newContext(parentContext, "MATCHED_URI"), "MATCHED_URI"},
            {newContext(newContext(parentContext, "PREVIOUSLY_MATCHED_URI"), "MATCHED_URI"),
                "PREVIOUSLY_MATCHED_URI/MATCHED_URI"},
            {newContext(newContext(
                newContext(parentContext, "FIRST_MATCHED_URI"), "PREVIOUSLY_MATCHED_URI"), "MATCHED_URI"),
                "FIRST_MATCHED_URI/PREVIOUSLY_MATCHED_URI/MATCHED_URI"},
            {newContext(newContext(parentContext, "PREVIOUSLY_MATCHED_URI"), ""), "PREVIOUSLY_MATCHED_URI"},

        };
    }

    @Test(dataProvider = "testData")
    public void shouldGetBaseUri(UriRouterContext context, String expectedBaseUri) {

        //When
        String baseUri = context.getBaseUri();

        //Then
        assertThat(baseUri).isEqualTo(expectedBaseUri);
    }

    private UriRouterContext newContext(Context parentContext, String matchedUri) {
        return uriRouterContext(parentContext).matchedUri(matchedUri).remainingUri("REMAINING").build();
    }

    @Test
    public void shouldLookupOnParentContextForOriginalUri() throws Exception {
        final URI originalUri = new URI("http://www.example.com");
        UriRouterContext parent = uriRouterContext(new RootContext()).originalUri(originalUri).build();
        UriRouterContext context = uriRouterContext(parent).build();

        assertThat(context.getOriginalUri()).isEqualTo(originalUri);
    }

    @Test
    public void shouldReturnTheFirstNotNullOriginalUri() throws Exception {
        final URI originalUri1 = null;
        final URI originalUri2 = new URI("http://www.forgerock.org");
        UriRouterContext context1 = uriRouterContext(new RootContext()).originalUri(originalUri1).build();
        UriRouterContext context2 = uriRouterContext(context1).originalUri(originalUri2).build();

        assertThat(context1.getOriginalUri()).isNull();
        assertThat(context2.getOriginalUri()).isEqualTo(originalUri2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void shouldFailWhenTryingToDefineMoreThanOneOriginalUri() throws URISyntaxException {
        final URI originalUri = new URI("http://www.example.com");
        UriRouterContext parent = uriRouterContext(new RootContext()).originalUri(originalUri).build();

        new UriRouterContext(parent, null, null, Collections.<String, String>emptyMap(),
                new URI("http://www.forgerock.org"));
    }

}
