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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.*;
import static org.forgerock.json.JsonValueFunctions.deepTransformBy;

import org.forgerock.util.Function;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class JsonValueTraverseFunctionTest {

    // @Checkstyle:off
    private Function<JsonValue, Object, JsonValueException> function =
            new Function<JsonValue, Object, JsonValueException>() {
                @Override
                public Object apply(JsonValue value) throws JsonValueException {
                    if (value.isString()) {
                        return value.asString() + "x";
                    }
                    return value.getObject();
                }
            };
    // @Checkstyle:on

    @Test
    public void shouldTransformSingleElement() throws JsonValueException {
        JsonValue input = json("1");
        JsonValue transformed = input.as(deepTransformBy(function));
        assertThat(transformed.isString()).isTrue();
        assertThat(transformed.asString()).isEqualTo("1x");
    }

    @Test
    public void shouldTransformElementsInCollection() throws JsonValueException {
        JsonValue input = json(array("1", "2", "3"));
        JsonValue transformed = input.as(deepTransformBy(function));
        assertThat(transformed.isList()).isTrue();
        assertThat(transformed.asList()).containsSequence("1x", "2x", "3x");
    }

    @Test
    public void shouldTransformElementsInMap() throws JsonValueException {
        JsonValue input = json(object(field("key1", "1"), field("key2", "2"), field("key3", "3")));
        JsonValue transformed = input.as(deepTransformBy(function));
        assertThat(transformed.isMap()).isTrue();
        assertThat(transformed.asMap()).containsValues("1x", "2x", "3x");
    }

    @Test
    public void testDeepTransform() throws JsonValueException {
        JsonValue input = json(object(
                field("key", "value"),
                field("array", array("1", "2", "3")),
                field("object", object(field("a", "1"), field("b", "2")))));
        JsonValue expected = json(object(
                field("key", "valuex"),
                field("array", array("1x", "2x", "3x")),
                field("object", object(field("a", "1x"), field("b", "2x")))));
        JsonValue transformed = input.as(deepTransformBy(function));
        assertThat(transformed.isEqualTo(expected)).isTrue();
    }
}
