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
package org.forgerock.json.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.io.IOException;

import org.forgerock.json.JsonValue;
import org.forgerock.util.encode.Base64;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class to provide helpful implementations of {@link JsonEncryptor} and {@link JsonDecryptor} based on the
 * Base64 encoding algorithm.
 */
public class JsonDecryptorUtilsTest {

    private static final String BASE64 = "base64";

    /** A Base64 implementation of {@link org.forgerock.json.crypto.JsonDecryptor} that decodes the string value */
    static final JsonDecryptor BASE64_DECRYPTOR = new JsonDecryptor() {

        private ObjectMapper mapper = new ObjectMapper();

        @Override
        public String getType() {
            return BASE64;
        }

        @Override
        public JsonValue decrypt(JsonValue value) throws JsonCryptoException {
            if (value.isString()) {
                byte[] decoded = Base64.decode(value.asString().getBytes());
                try {
                    return new JsonValue(mapper.readValue(decoded, Object.class), value.getPointer());
                } catch (IOException e) {
                    throw new JsonCryptoException(e);
                }
            }
            return value.copy();
        }
    };

    /** A Base64 implementation of {@link org.forgerock.json.crypto.JsonEncryptor} that encodes the {@link JsonValue} */
    static final JsonEncryptor BASE64_ENCRYPTOR = new JsonEncryptor() {

        private ObjectMapper mapper = new ObjectMapper();

        @Override
        public String getType() {
            return BASE64;
        }

        @Override
        public JsonValue encrypt(JsonValue value) throws JsonCryptoException {
            try {
                return new JsonValue(Base64.encode(mapper.writeValueAsBytes(value.getObject())), value.getPointer());
            } catch (JsonProcessingException e) {
                throw new JsonCryptoException(e);
            }
        }

    };

    @Test
    public void shouldDecodeEncodedWithBase64() throws Exception {
        JsonValue value = json(object(field("foo", "bar")));
        JsonValue result = BASE64_DECRYPTOR.decrypt(BASE64_ENCRYPTOR.encrypt(value));
        assertThat(result.isEqualTo(value)).isTrue();
    }

}

