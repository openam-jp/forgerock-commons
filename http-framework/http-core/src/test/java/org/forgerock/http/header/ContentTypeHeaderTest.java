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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.http.header;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.http.header.ContentTypeHeader.NAME;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.TreeMap;

import org.assertj.core.data.MapEntry;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class tests the content type header. see <a href="http://www.ietf.org/rfc/rfc2616.txt">RFC 2616</a> §14.17.
 * boundary see http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.2 .
 * Header field example :
 * <pre>
 * Content-Type: text/html; charset=utf-8
 * </pre>
 */
@SuppressWarnings("javadoc")
public class ContentTypeHeaderTest {

    /** An invalid content type header - The separator is not correct. */
    private static final String INVALID_CT_HEADER = "text/html# charset=ISO-8859-4";

    @DataProvider
    private Object[][] contentTypeHeaderProvider() {
        return new Object[][] {
            // content-type | type | charset | boundary
            { "image/gif", "image/gif", null, null },
            { "text/html; charset=ISO-8859-4", "text/html", "ISO-8859-4", null },
            { "multipart/mixed; boundary=gc0p4Jq0M2Yt08jU534c0p", "multipart/mixed", null,
                "gc0p4Jq0M2Yt08jU534c0p" },
            { "text/html; charset=utf-8", "text/html", "UTF-8", null } };
    }

    @Test(dataProvider = "nullOrEmptyDataProvider", dataProviderClass = StaticProvider.class)
    public void testContentTypeHeaderAllowsNullOrEmptyString(final String cheader) {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(cheader);
        assertThat(cth.getType()).isNull();
        assertThat(cth.getCharset()).isNull();
        assertThat(cth.getBoundary()).isNull();
        assertThat(cth.getValues()).isNullOrEmpty();
        assertThat(cth.getAdditionalParameters()).isEmpty();
    }

    @Test
    public void testContentTypeHeaderFromNullMessage() {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf((Message) null);
        assertThat(cth.getType()).isNull();
        assertThat(cth.getCharset()).isNull();
        assertThat(cth.getBoundary()).isNull();
        assertThat(cth.getValues()).isNullOrEmpty();
        assertThat(cth.getAdditionalParameters()).isEmpty();
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderFromString(final String cheader, final String type, final String charset,
            final String boundary) {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(cheader);
        assertThat(cth.getType()).isEqualTo(type);
        assertThat(cth.getCharset()).isEqualTo(charset != null ? Charset.forName(charset) : null);
        assertThat(cth.getBoundary()).isEqualTo(boundary);
        assertThat(cth.getAdditionalParameters()).isEmpty();
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderToMessageRequest(final String cheader, final String type, final String charset,
            final String boundary) {
        final Request request = new Request();
        assertThat(request.getHeaders().get(NAME)).isNull();
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(cheader);
        request.getHeaders().put(cth);
        assertThat(request.getHeaders().get(NAME)).isNotNull();
        assertThat(request.getHeaders().get(NAME).getValues()).containsOnly(cheader);
        assertThat(cth.getAdditionalParameters()).isEmpty();
    }

    @Test
    public void testContentTypeHeaderFromInvalidString() {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(INVALID_CT_HEADER);
        assertThat(cth.getType()).isEqualTo(INVALID_CT_HEADER);
        assertThat(cth.getCharset()).isEqualTo(null);
        assertThat(cth.getBoundary()).isEqualTo(null);
        assertThat(cth.getAdditionalParameters()).isEmpty();
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderFromMessageResponse(final String cheader, final String type, final String charset,
            final String boundary) {
        // Creates response.
        final Response response = new Response(Status.OK);
        assertThat(response.getHeaders().get(NAME)).isNull();
        response.getHeaders().put(NAME, cheader);
        assertThat(response.getHeaders().get(NAME)).isNotNull();

        // Creates content-type header from response.
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(response);
        assertThat(cth.getType()).isEqualTo(type);
        assertThat(cth.getCharset()).isEqualTo(charset != null ? Charset.forName(charset) : null);
        assertThat(cth.getBoundary()).isEqualTo(boundary);
        assertThat(cth.getAdditionalParameters()).isEmpty();
    }

    @Test(dataProvider = "contentTypeHeaderProvider")
    public void testContentTypeHeaderFromMessageRequest(final String cheader, final String type, final String charset,
            final String boundary) {
        // Creates request.
        final Request request = new Request();
        assertThat(request.getHeaders().get(NAME)).isNull();
        request.getHeaders().put(NAME, cheader);
        assertThat(request.getHeaders().get(NAME)).isNotNull();

        // Creates content-type header from request.
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(request);
        assertThat(cth.getType()).isEqualTo(type);
        assertThat(cth.getCharset()).isEqualTo(charset != null ? Charset.forName(charset) : null);
        assertThat(cth.getBoundary()).isEqualTo(boundary);
        assertThat(cth.getAdditionalParameters()).isEmpty();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testAdditionalParamsRejectNull() {
        new ContentTypeHeader(null, null, null, null);
        fail("Expecting NullPointerException");
    }

    @DataProvider
    private Object[][] contentTypeHeaderProviderWithParams() {
        return new Object[][]{
            // content-type | type | charset | boundary | parameters
            { "application/octet-stream; param=value", "application/octet-stream", null, null,
                new MapEntry[]{ entry("param", "value") } },
            { "application/octet-stream; param1=value1; param2=value2", "application/octet-stream", null, null,
                new MapEntry[]{ entry("param1", "value1"), entry("param2", "value2") } },
            { "application/octet-stream; boundary=gc0p4Jq0M2Yt08jU534c0p; param=value", "application/octet-stream",
                null, "gc0p4Jq0M2Yt08jU534c0p", new MapEntry[]{ entry("param", "value") } },
            { "application/octet-stream; boundary=gc0p4Jq0M2Yt08jU534c0p; param1=value1; param2=value2",
                "application/octet-stream", null, "gc0p4Jq0M2Yt08jU534c0p",
                new MapEntry[]{ entry("param1", "value1"), entry("param2", "value2") } },
            // Params should have a value as-per the spec but allowing for compatibility with known use-cases.
            { "application/octet-stream; emptyparam", "application/octet-stream", null, null,
                new MapEntry[]{ entry("emptyparam", null) } },
            { "application/octet-stream; emptyparam; param=value", "application/octet-stream", null, null,
                new MapEntry[]{ entry("emptyparam", null), entry("param", "value") } },
            { "application/octet-stream; boundary=gc0p4Jq0M2Yt08jU534c0p; emptyparam", "application/octet-stream",
                null, "gc0p4Jq0M2Yt08jU534c0p", new MapEntry[]{ entry("emptyparam", null) } },
            { "application/octet-stream; boundary=gc0p4Jq0M2Yt08jU534c0p; emptyparam; param=value",
                "application/octet-stream", null, "gc0p4Jq0M2Yt08jU534c0p",
                new MapEntry[]{ entry("emptyparam", null), entry("param", "value") } },
            { "application/octet-stream; charset=UTF-8; boundary=gc0p4Jq0M2Yt08jU534c0p; emptyparam; param=value",
                "application/octet-stream", "UTF-8", "gc0p4Jq0M2Yt08jU534c0p",
                new MapEntry[]{ entry("emptyparam", null), entry("param", "value") } }
        };
    }

    @Test(dataProvider = "contentTypeHeaderProviderWithParams")
    public void testContentTypeHeaderWithParams(final String contentTypeHeader,
                                                final String type,
                                                final String charset,
                                                final String boundary,
                                                final MapEntry<String, String>[] additionalParameters) {
        final ContentTypeHeader cth = ContentTypeHeader.valueOf(contentTypeHeader);

        assertThat(cth.getType()).isEqualTo(type);
        assertThat(cth.getCharset()).isEqualTo(charset != null ? Charset.forName(charset) : null);
        assertThat(cth.getBoundary()).isEqualTo(boundary);
        assertThat(cth.getAdditionalParameters()).containsOnly(additionalParameters);
    }

    @Test(dataProvider = "contentTypeHeaderProviderWithParams")
    public void testSerializedValues(final String contentTypeHeader,
                                     final String type,
                                     final String charset,
                                     final String boundary,
                                     final MapEntry<String, String>[] additionalParameters) {
        ContentTypeHeader cth = new ContentTypeHeader(type, charset, boundary, asMap(additionalParameters));

        // Based on knowing the parameter names are stored in alphabetical order.
        assertThat(cth.getValues()).containsOnly(contentTypeHeader);
    }

    private Map<String, String> asMap(MapEntry<String, String>[] toConvert) {
        Map<String, String> result = new TreeMap<>();
        for (MapEntry<String, String> entry : toConvert) {
            result.put(entry.key, entry.value);
        }
        return result;
    }
}
