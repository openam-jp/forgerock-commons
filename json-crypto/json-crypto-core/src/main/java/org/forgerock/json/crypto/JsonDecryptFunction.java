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

import static org.forgerock.json.JsonValueFunctions.identity;
import static org.forgerock.util.Reject.checkNotNull;

import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.json.JsonValueTraverseFunction;

/**
 * Create a new {@link JsonValue} by applying a decryptor.
 */
public class JsonDecryptFunction extends JsonValueTraverseFunction {

    /** Decryptor to apply to JSON values. */
    private final JsonDecryptor decryptor;

    /**
     * Constructs a function to apply a decryptor.
     *
     * @param decryptor the decryptor to apply to JSON values.
     * @throws NullPointerException if {@code decryptor} is {@code null}.
     */
    public JsonDecryptFunction(JsonDecryptor decryptor) {
        super(identity());
        this.decryptor = checkNotNull(decryptor);
    }

    @Override
    protected Object traverseMap(JsonValue value) {
        if (JsonCrypto.isJsonCrypto(value)) {
            JsonCrypto crypto = new JsonCrypto(value);
            if (crypto.getType().equals(decryptor.getType())) { // only attempt decryption if type matches
                try {
                    JsonValue decrypted = decryptor.decrypt(crypto.getValue());
                    // Set a correct JsonPointer to the decrypted JsonValue (the decrypted one ends with /$crypto/value)
//                    decrypted = new JsonValue(decrypted.getObject(), value.getPointer());
                    // The decrypted JsonValue may contain a structure that itself contains some crypted JsonValue.
                    return apply(decrypted);
                } catch (JsonCryptoException jce) {
                    throw new JsonValueException(value, jce);
                }
            }
        }
        return super.traverseMap(value);
    }

}
