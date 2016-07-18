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
 * Copyright 2011-2016 ForgeRock AS.
 */

package org.forgerock.json.crypto.simple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.forgerock.json.JsonValue;
import org.forgerock.json.crypto.JsonCryptoException;
import org.forgerock.json.crypto.JsonEncryptor;
import org.forgerock.util.encode.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Encrypts a JSON value into an {@code x-simple-encryption} type {@code $crypto} JSON object.
 */
public class SimpleEncryptor implements JsonEncryptor {

    /** The type of cryptographic representation that this encryptor supports. */
    public static final String TYPE = "x-simple-encryption";

    static final String MAC_ALGORITHM = "HmacSHA256";
    static final int MAC_KEY_SIZE = 32;

    static final int ASYMMETRIC_AES_KEY_SIZE = 128 / 8;

    /** Converts between Java objects and JSON constructs. */
    private final ObjectMapper mapper = new ObjectMapper();

    /** The cipher to encrypt with. */
    private String cipher;

    /** The key to encrypt with. */
    private Key key;

    /** The key alias to list in the encrypted object. */
    private String alias;

    /**
     * Constructs a new simple encryptor for the specified cipher, key and alias.
     *
     * @param cipher the cipher to encrypt with.
     * @param key the key to encrypt with.
     * @param alias the key alias to list in the encrypted object.
     */
    public SimpleEncryptor(String cipher, Key key, String alias) {
        this.cipher = cipher;
        this.key = key;
        this.alias = alias;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Encrypts with a symmetric cipher.
     *
     * @param object the value to be encrypted.
     * @return the encrypted value.
     * @throws GeneralSecurityException if a cryptographic operation failed.
     * @throws IOException if an I/O exception occurred.
     */
    private Object symmetric(Object object) throws GeneralSecurityException, IOException {
        Cipher symmetric = Cipher.getInstance(cipher);

        HKDFKeyGenerator.HKDFMasterKey masterKey = HKDFKeyGenerator.extractMasterKey(key.getEncoded());
        Key encryptionKey = HKDFKeyGenerator.expandKey(masterKey, "AES", key.getEncoded().length);
        Key macKey = HKDFKeyGenerator.expandKey(masterKey, MAC_ALGORITHM, MAC_KEY_SIZE);

        symmetric.init(Cipher.ENCRYPT_MODE, encryptionKey);
        String data = Base64.encode(symmetric.doFinal(mapper.writeValueAsBytes(object)));
        byte[] iv = symmetric.getIV();
        byte[] salt = masterKey.getSalt();
        HashMap<String, Object> result = new HashMap<>();
        result.put("cipher", this.cipher);
        result.put("key", this.alias);
        result.put("data", data);
        if (iv != null) {
            result.put("iv", Base64.encode(iv));
        }
        if (salt != null) {
            result.put("salt", Base64.encode(salt));
        }
        result.put("mac", Base64.encode(mac(result, macKey)));
        return result;
    }

    /**
     * Encrypts using an asymmetric cipher.
     *
     * @param object the value to be encrypted.
     * @return the encrypted value.
     * @throws GeneralSecurityException if a cryptographic operation failed.
     * @throws IOException if an I/O exception occurred.
     */
    private Object asymmetric(Object object) throws GeneralSecurityException, IOException {
        String symmetricCipher = "AES/CBC/PKCS5Padding";
        KeyGenerator generator = KeyGenerator.getInstance(HKDFKeyGenerator.HMAC_ALGORITHM);
        generator.init(HKDFKeyGenerator.HASH_LEN * 8);
        SecretKey sessionKey = generator.generateKey();

        HKDFKeyGenerator.HKDFMasterKey masterKey = HKDFKeyGenerator.extractMasterKey(sessionKey.getEncoded());
        final byte[] salt = masterKey.getSalt();

        final Key encryptionKey = HKDFKeyGenerator.expandKey(masterKey, "AES", ASYMMETRIC_AES_KEY_SIZE);
        final Key macKey = HKDFKeyGenerator.expandKey(masterKey, MAC_ALGORITHM, MAC_KEY_SIZE);

        Cipher symmetric = Cipher.getInstance(symmetricCipher);
        symmetric.init(Cipher.ENCRYPT_MODE, encryptionKey);
        byte[] iv = symmetric.getIV();
        String data = Base64.encode(symmetric.doFinal(mapper.writeValueAsBytes(object)));

        Cipher asymmetric = Cipher.getInstance(cipher);
        asymmetric.init(Cipher.ENCRYPT_MODE, key);
        HashMap<String, Object> keyObject = new HashMap<>();
        keyObject.put("cipher", this.cipher);
        keyObject.put("key", this.alias);
        keyObject.put("data", Base64.encode(asymmetric.doFinal(sessionKey.getEncoded())));

        HashMap<String, Object> result = new HashMap<>();
        result.put("cipher", symmetricCipher);
        result.put("key", keyObject);
        result.put("data", data);
        if (iv != null) {
            result.put("iv", Base64.encode(iv));
        }
        if (salt != null) {
            result.put("salt", Base64.encode(salt));
        }

        result.put("mac", Base64.encode(mac(result, macKey)));
        return result;
    }

    @Override
    public JsonValue encrypt(JsonValue value) throws JsonCryptoException {
        Object object = value.getObject();
        try {
            return new JsonValue((key instanceof SecretKey ? symmetric(object) : asymmetric(object)));
        } catch (GeneralSecurityException | IOException e) {
            throw new JsonCryptoException(e);
        }
    }

    static byte[] mac(final Map<String, Object> input, final Key macKey) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(macKey);
            updateMac(mac, input);
            // Truncate the MAC to half size
            return Arrays.copyOfRange(mac.doFinal(), 0, MAC_KEY_SIZE / 2);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateMac(final Mac mac, final Map<String, Object> objectMap) {
        for (final Map.Entry<String, Object> entry : new TreeMap<>(objectMap).entrySet()) {
            mac.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
            if (entry.getValue() instanceof Map) {
                updateMac(mac, (Map<String, Object>) entry.getValue());
            } else {
                mac.update(entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
