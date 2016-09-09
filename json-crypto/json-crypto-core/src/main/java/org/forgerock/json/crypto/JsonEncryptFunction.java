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

import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.json.JsonValue;
import org.forgerock.util.Function;

/**
 * Create a new {@link JsonValue} by applying an encryptor.
 */
public class JsonEncryptFunction implements Function<JsonValue, JsonValue, JsonCryptoException> {

    /** Encryptor to apply to JSON values. */
    private final JsonEncryptor encryptor;

    /**
     * Constructs a function to apply an encryptor.
     *
     * @param encryptor the encryptor to apply to JSON values.
     * @throws NullPointerException if {@code encryptor} is {@code null}.
     */
    public JsonEncryptFunction(JsonEncryptor encryptor) {
        this.encryptor = checkNotNull(encryptor);
    }

    @Override
    public JsonValue apply(JsonValue value) throws JsonCryptoException {
        return new JsonCrypto(encryptor.getType(), encryptor.encrypt(value)).toJsonValue();
    }
}
