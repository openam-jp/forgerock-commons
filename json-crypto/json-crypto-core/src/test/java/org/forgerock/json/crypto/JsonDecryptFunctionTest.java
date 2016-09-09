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
import static org.forgerock.json.crypto.JsonDecryptorUtilsTest.BASE64_DECRYPTOR;
import static org.forgerock.json.crypto.JsonDecryptorUtilsTest.BASE64_ENCRYPTOR;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class JsonDecryptFunctionTest {

    @Test
    public void shouldDecryptEncryptedJsonValue() throws Exception {
        JsonValue value = new JsonValue("ForgeRock", new JsonPointer("/a/b/0"));
        JsonValue encryptedValue = value.as(new JsonEncryptFunction(BASE64_ENCRYPTOR));

        JsonValue decryptedValue = encryptedValue.as(new JsonDecryptFunction(BASE64_DECRYPTOR));

        assertThat(decryptedValue.isEqualTo(value)).isTrue();
        assertThat(decryptedValue.getPointer()).isEqualTo(value.getPointer());
    }

    @Test
    public void shouldNotDecryptJsonValueIfNotMatchingCryptoType() throws Exception {
        JsonValue crypted = new JsonCrypto("rot13", json("SbetrEbpx")).toJsonValue();
        assertThat(decode64(crypted).isEqualTo(crypted)).isTrue();
    }

    @Test
    public void shouldDecryptRecursivelyCryptedValue() throws Exception {
        String forgeRock = "ForgeRock";
        // First encryption
        JsonValue crypted1 = encode64(new JsonValue(object(field("bar", forgeRock)), new JsonPointer("/a/b/0")));
        // crypted1 is : { "$crypto": { "type": "base64", "value": "eyAiYmFyIjogIkZvcmdlUm9jayIgfQ==" } }

        // Second encryption
        JsonValue crypted2 = encode64(new JsonValue(object(field("foo", crypted1.getObject())),
                new JsonPointer("/foo")));
        // crypted2 is : { "$crypto": { "type": "base64", "value": "eyAiZm9vIjogey.....UT09IiB9IH0gfQ==" } }

        // We expect recursive decryption and to get : { "foo": { "bar": "ForgeRock" } }
        assertThat(decode64(crypted2).get("foo").get("bar").asString()).isEqualTo(forgeRock);
    }

    private static JsonValue encode64(JsonValue value) throws JsonCryptoException {
        return value.as(new JsonEncryptFunction(BASE64_ENCRYPTOR));
    }

    private static JsonValue decode64(JsonValue value) throws JsonCryptoException {
        return value.as(new JsonDecryptFunction(BASE64_DECRYPTOR));
    }

}
