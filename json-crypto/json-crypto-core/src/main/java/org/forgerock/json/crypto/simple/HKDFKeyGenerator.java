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

package org.forgerock.json.crypto.simple;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.forgerock.util.Reject;

/**
 * Implements the <a href="https://tools.ietf.org/rfc/rfc5869.txt">HKDF</a> key deriviation function to allow a
 * single input key to be expanded into multiple component keys.
 */
final class HKDFKeyGenerator {
    private static final String MASTER_KEY_ALGORITHM = "HKDF";
    static final String HMAC_ALGORITHM = "HmacSHA256";
    static final int HASH_LEN = 256 / 8;

    /**
     * Secure random generator used for generating unique salt to strength the entropy of the master key. Note that
     * despite any warnings in container logs on shutdown, this will not leak memory as SecureRandom is a core JRE
     * class loaded with the system classloader rather than the application classloader.
     */
    private static final ThreadLocal<SecureRandom> THREAD_LOCAL_SECURE_RANDOM = new ThreadLocal<SecureRandom>() {
        @Override
        protected SecureRandom initialValue() {
            return new SecureRandom();
        }
    };

    /**
     * The HKDF "extract" phase that generates a master key from some input key material. This method adds 128-bits
     * of random salt to the derived key. This master key should not be used directly, but instead fed into
     * {@link #expandKey(HKDFMasterKey, String, String, int)} to derive a specific key for a particular usage.
     *
     * @param inputKeyMaterial the input master key material.
     * @return the derived master key.
     */
    static HKDFMasterKey extractMasterKey(byte[] inputKeyMaterial) {
        final byte[] salt = new byte[16];
        THREAD_LOCAL_SECURE_RANDOM.get().nextBytes(salt);
        return extractMasterKey(inputKeyMaterial, salt);
    }

    /**
     * The HKDF "extract" phase that generates a master key from some input key material. This method uses the random
     * salt value passed as a parameter. This master key should not be used directly, but instead fed into
     * {@link #expandKey(HKDFMasterKey, String, String, int)} to derive a specific key for a particular usage.
     *
     * @param inputKeyMaterial the input master key material.
     * @param salt the random salt to use when deriving the master key. Should be at least 128 bits and uniformly
     *             random.
     * @return the derived master key.
     */
    static HKDFMasterKey extractMasterKey(byte[] inputKeyMaterial, byte[] salt) {
        Reject.ifNull(inputKeyMaterial);
        Reject.ifFalse(inputKeyMaterial.length >= 16, "Input key should be at least 128 bits");

        // As per the RFC, if salt is not supplied then it should be HASH_LEN zeros
        if (salt == null || salt.length == 0) {
            salt = new byte[HASH_LEN];
        }
        return new HKDFMasterKey(getHmac(new SecretKeySpec(salt, HMAC_ALGORITHM)).doFinal(inputKeyMaterial), salt);
    }

    /**
     * Expands a master key into a derived key for a specific purpose. The key is derived by repeatedly applying
     * HMAC-SHA-256 using the master key as the key and the given parameters (together with an incrementing counter) as
     * input.
     *
     * @param masterKey the HKDF master key.
     * @param outputKeyAlgorithm the algorithm for which the derived key is to be used, e.g. {@literal "AES"}.
     * @param purpose an arbitrary application-specific string describing the purpose of this key (e.g. {@literal
     * "OpenID Connect token signing"}.
     * @param outputKeySize the output key size, in bytes. This can be between 0 and 8160 bytes.
     * @return the derived key.
     */
    static Key expandKey(HKDFMasterKey masterKey, String outputKeyAlgorithm, String purpose, int outputKeySize) {
        return expandKey(masterKey, outputKeyAlgorithm, purpose.getBytes(StandardCharsets.UTF_8), outputKeySize);
    }

    /**
     * Expands a master key into a derived key for a specific purpose. The key is derived by repeatedly applying
     * HMAC-SHA-256 using the master key as the key and the given parameters (together with an incrementing counter) as
     * input.
     *
     * @param masterKey the HKDF master key.
     * @param outputKeyAlgorithm the algorithm for which the derived key is to be used, e.g. {@literal "AES"}.
     * @param info an arbitrary application-specific byte-string to include in the key derivation.
     * @param outputKeySize the output key size, in bytes. This can be between 0 and 8160 bytes.
     * @return the derived key.
     */
    static Key expandKey(HKDFMasterKey masterKey, String outputKeyAlgorithm, byte[] info, int outputKeySize) {
        Reject.ifFalse(outputKeySize <= 255 * HASH_LEN, "Cannot derive more than " + (255 * HASH_LEN)
                + " bytes of key material");

        final byte[] output = new byte[outputKeySize];

        final Mac hmac = getHmac(masterKey);

        int block = 1;
        for (int i = 0; i < outputKeySize; i += HASH_LEN) {
            if (i > 0) {
                hmac.update(output, i - HASH_LEN, HASH_LEN);
            }
            hmac.update(info);
            hmac.update((byte) block++);
            System.arraycopy(hmac.doFinal(), 0, output, i, Math.min(HASH_LEN, outputKeySize - i));
            hmac.reset();
        }

        return new SecretKeySpec(output, outputKeyAlgorithm);
    }

    /**
     * Expands a master key into a derived key for a specific purpose. The key is derived by repeatedly applying
     * HMAC-SHA-256 using the master key as the key and the given parameters (together with an incrementing counter) as
     * input. This is identical to the {@link #expandKey(HKDFMasterKey, String, String, int)} method except that the
     * {@code outputKeyAlgorithm} is also used as the {@code purpose} when deriving the key.
     *
     * @param masterKey the HKDF master key.
     * @param outputKeyAlgorithm the algorithm for which the derived key is to be used, e.g. {@literal "AES"}.
     * @param outputKeySize the output key size, in bytes. This can be between 0 and 8160 bytes.
     * @return the derived key.
     */
    static Key expandKey(HKDFMasterKey masterKey, String outputKeyAlgorithm, int outputKeySize) {
        return expandKey(masterKey, outputKeyAlgorithm, outputKeyAlgorithm, outputKeySize);
    }

    private static Mac getHmac(Key key) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(key);
            return hmac;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid HKDF key", e);
        }
    }

    /**
     * A secret key designed to be used as the master key for HKDF key generation. In addition to the secret key
     * material, this also has a non-secret random salt parameter.
     */
    static class HKDFMasterKey extends SecretKeySpec {
        private static final long serialVersionUID = 1L;
        private final byte[] salt;

        HKDFMasterKey(final byte[] keyBytes, final byte[] salt) {
            super(keyBytes, MASTER_KEY_ALGORITHM);
            this.salt = salt;
        }

        byte[] getSalt() {
            return salt;
        }
    }

    private HKDFKeyGenerator() {
        // Utility class
    }
}
