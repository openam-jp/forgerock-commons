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
 */
package org.forgerock.audit.event;

import static java.util.Arrays.*;
import static org.fest.assertions.api.Assertions.*;
import static org.forgerock.audit.event.AuditEventBuilderTest.OpenProductAccessAuditEventBuilder.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AuditEventBuilderTest {

    @Test(expectedExceptions= { IllegalStateException.class })
    public void ensureAuditEventContainsMandatoryAttributes() throws Exception {
        productAccessEvent().toEvent();
    }

    @Test
    public void ensureAuditEventContainsTimestampEvenIfNotAdded() throws Exception {
        AuditEvent event = productAccessEvent()
                .transactionId("transactionId")
                .toEvent();
        JsonValue value = event.getValue();
        assertThat(value.get("timestamp").asString()).isNotNull().isNotEmpty();
    }

    @Test(expectedExceptions= { IllegalStateException.class })
    public void ensureAuditEventContainsTransactionId() throws Exception {
        productAccessEvent()
                .timestamp(System.currentTimeMillis())
                .toEvent();
    }

    /**
     * Example builder of audit access events for some imaginary product "OpenProduct".
     */
    @SuppressWarnings("rawtypes")
    static class OpenProductAccessAuditEventBuilder<T extends OpenProductAccessAuditEventBuilder<T>>
        extends AuditEventBuilder.AccessAuditEventBuilder<T> {

        private OpenProductAccessAuditEventBuilder() {
            super();
        }

        public static <T> OpenProductAccessAuditEventBuilder<?> productAccessEvent() {
            return new OpenProductAccessAuditEventBuilder();
        }

        public T openField(String v) {
            jsonValue.put("open", v);
            return self();
        }

        @Override
        protected T self() {
            return (T) this;
        }
    }

    @Test
    public void ensureEventIsCorrectlyBuilt() {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put("Content-Length", asList("200"));
        headers.put("Content-Type", asList("application/json"));

        AuditEvent event = productAccessEvent()
                .transactionId("transactionId")
                .timestamp(1427293286239l)
                .messageId("IDM-sync-10")
                .client("cip", 1203)
                .server("sip", 80)
                .authorizationId("managed/user", "aegloff", "openidm-admin", "openidm-authorized")
                .authenticationId("someone@forgerock.com")
                .resourceOperation("action", "reconcile")
                .http("GET", "/some/path", "p1=v1&p2=v2", headers)
                .response("200", 12)
                .openField("value")
                .aField("field", "fieldValue")
                .toEvent();

        JsonValue value = event.getValue();
        assertThat(value.get("transactionId").asString()).isEqualTo("transactionId");
        assertThat(value.get("timestamp").asString()).isEqualTo("2015-03-25T15:21:26.239+01:00");
        assertThat(value.get("messageId").asString()).isEqualTo("IDM-sync-10");
        assertThat(value.get("server").get("ip").asString()).isEqualTo("sip");
        assertThat(value.get("server").get("port").asLong()).isEqualTo(80);
        assertThat(value.get("http").get("method").asString()).isEqualTo("GET");
        assertThat(value.get("http").get("headers").asMapOfList(String.class)).isEqualTo(headers);
        assertThat(value.get("authorizationId").get("id").asString()).isEqualTo("aegloff");
        assertThat(value.get("resourceOperation").get("method").asString()).isEqualTo("action");
        assertThat(value.get("response").get("status").asString()).isEqualTo("200");
        assertThat(value.get("response").get("elapsedTime").asLong()).isEqualTo(12);
        assertThat(value.get("open").getObject()).isEqualTo("value");
        assertThat(value.get("field").getObject()).isEqualTo("fieldValue");
    }


    @Test
    public void ensureBuilderMethodsCanBeCalledInAnyOrder() {
        AuditEvent event1 = productAccessEvent()
                .server("ip", 80)
                .client("cip", 1203)
                .openField("value")
                .transactionId("transactionId")
                .timestamp(1427293286239l)
                .toEvent();
        assertEvent(event1);

        AuditEvent event2 = productAccessEvent()
                .client("cip", 1203)
                .openField("value")
                .server("ip", 80)
                .transactionId("transactionId")
                .timestamp(1427293286239l)
                .toEvent();
        assertEvent(event2);

        AuditEvent event3 = productAccessEvent()
                .openField("value")
                .transactionId("transactionId")
                .client("cip", 1203)
                .server("ip", 80)
                .transactionId("transactionId")
                .timestamp(1427293286239l)
                .toEvent();
        assertEvent(event3);

        AuditEvent event4 = productAccessEvent()
                .transactionId("transactionId")
                .client("cip", 1203)
                .openField("value")
                .server("ip", 80)
                .transactionId("transactionId")
                .timestamp(1427293286239l)
                .toEvent();

        assertEvent(event4);

    }


    @Test
    public void eventWithNoHeader() {
        Map<String, List<String>> headers = Collections.<String, List<String>>emptyMap();

        AuditEvent event = productAccessEvent()
                .transactionId("transactionId")
                .timestamp(1427293286239l)
                .http("GET", "/some/path", "p1=v1&p2=v2", headers)
                .toEvent();

        JsonValue value = event.getValue();
        assertThat(value.get("transactionId").asString()).isEqualTo("transactionId");
        assertThat(value.get("timestamp").asString()).isEqualTo("2015-03-25T15:21:26.239+01:00");
        assertThat(value.get("http").get("headers").asMapOfList(String.class)).isEqualTo(headers);
    }



    private void assertEvent(AuditEvent event) {
        JsonValue value = event.getValue();
        assertThat(value.get("open").getObject()).isEqualTo("value");
        assertThat(value.get("server").get("ip").asString()).isEqualTo("ip");
        assertThat(value.get("server").get("port").asLong()).isEqualTo(80);
        assertThat(value.get("client").get("ip").asString()).isEqualTo("cip");
        assertThat(value.get("client").get("port").asLong()).isEqualTo(1203);
        assertThat(value.get("transactionId").asString()).isEqualTo("transactionId");
        assertThat(value.get("timestamp").asString()).isEqualTo("2015-03-25T15:21:26.239+01:00");
    }
}
