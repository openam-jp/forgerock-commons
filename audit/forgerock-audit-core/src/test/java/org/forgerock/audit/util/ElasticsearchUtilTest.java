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

package org.forgerock.audit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.data.MapEntry;
import org.forgerock.json.JsonValue;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.audit.util.ElasticsearchUtil.*;
import static org.forgerock.http.util.Json.readJson;
import static org.forgerock.json.JsonValue.*;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ElasticsearchUtilTest {

    private static final String RESOURCE_PATH = "/org/forgerock/audit/util/";

    private static final MapEntry<String, String> FIELD_NAME_PAIR =
            MapEntry.entry("org_forgerock_authentication_principal", "org.forgerock.authentication.principal");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Test that all periods in JSON keys will be replaced by underscores, as required by Elasticsearch.
     */
    @Test
    public void normalizeJsonWithPeriodsInKeysTest() throws Exception {
        // given
        final JsonValue beforeNormalization = resourceAsJsonValue(RESOURCE_PATH + "authEventBeforeNormalization.json");
        final JsonValue afterNormalization = resourceAsJsonValue(RESOURCE_PATH + "authEventAfterNormalization.json");

        final String beforeNormalizationString = OBJECT_MAPPER.writeValueAsString(beforeNormalization.getObject());
        final String afterNormalizationString = OBJECT_MAPPER.writeValueAsString(afterNormalization.getObject());
        assertThat(beforeNormalizationString).isNotEqualTo(afterNormalizationString);

        // when
        final String resultString = replaceKeyPeriodsWithUnderscores(beforeNormalizationString);

        // then
        final JsonValue result = json(readJson(resultString));
        final JsonValue normalizedField = result.get(NORMALIZED_FIELD);
        result.remove(NORMALIZED_FIELD);

        assertThat(OBJECT_MAPPER.writeValueAsString(result.getObject())).isEqualTo(afterNormalizationString);
        assertThat(normalizedField.isNotNull()).isTrue();

        final JsonValue fieldNames = normalizedField.get(FIELD_NAMES_FIELD);
        assertThat(fieldNames.isNotNull()).isTrue();
        assertThat(fieldNames.asMap()).containsExactly(FIELD_NAME_PAIR);
    }

    @Test
    public void denormalizeJsonWithPeriodsInKeysTest() throws Exception {
        // given
        final JsonValue beforeNormalization = resourceAsJsonValue(RESOURCE_PATH + "authEventBeforeNormalization.json");
        final JsonValue afterNormalization = resourceAsJsonValue(RESOURCE_PATH + "authEventAfterNormalization.json");
        assertThat(beforeNormalization).isNotEqualTo(afterNormalization);

        final Map<String, Object> normalized = new LinkedHashMap<>(1);
        normalized.put(ElasticsearchUtil.FIELD_NAMES_FIELD,
                Collections.singletonMap(FIELD_NAME_PAIR.key, FIELD_NAME_PAIR.value));

        // when
        final JsonValue result = restoreKeyPeriods(afterNormalization, JsonValue.json(normalized));

        // then
        assertThat(OBJECT_MAPPER.writeValueAsString(result.getObject()))
                .isEqualTo(OBJECT_MAPPER.writeValueAsString(beforeNormalization.getObject()));
    }

    @Test
    public void renameFieldTest() throws Exception {
        // given
        final JsonValue value = json(object(field("oldName", "value")));

        // when
        renameField(value, "oldName", "newName");

        // then
        assertThat(value.get("oldName").isNull()).isTrue();
        assertThat(value.get("newName").isNotNull()).isTrue();
    }

    @Test
    public void renameFieldToExistingFieldTest() throws Exception {
        // given
        final JsonValue value = json(object(field("oldName", "value"), field("existingName", "value")));


        try {
            // when
            renameField(value, "oldName", "existingName");

            // then
            Assert.fail("Expected IllegalStateException, but none occured");
        } catch (Exception e) {
            if (!(e instanceof IllegalStateException)) {
                Assert.fail("Expected IllegalStateException", e);
            }
            assertThat(value.get("existingName").isNull()).isTrue();
            assertThat(value.get("oldName").isNotNull()).isTrue();
        }
    }

    private JsonValue resourceAsJsonValue(final String resourcePath) throws Exception {
        try (final InputStream configStream = getClass().getResourceAsStream(resourcePath)) {
            return new JsonValue(OBJECT_MAPPER.readValue(configStream, Map.class));
        }
    }
}
