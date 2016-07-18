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


import static org.assertj.core.api.Assertions.assertThat;

import java.security.Key;

import javax.xml.bind.DatatypeConverter;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class HKDFKeyGeneratorTest {

    @Test(expectedExceptions = NullPointerException.class)
    public void shouldRejectNullKeyMaterial() {
        HKDFKeyGenerator.extractMasterKey(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "weakKeyMaterial")
    public void shouldRejectWeakKeyMaterial(byte[] key) {
        HKDFKeyGenerator.extractMasterKey(key);
    }

    // Test cases from https://tools.ietf.org/html/rfc5869#appendix-A

    @Test
    public void shouldPassRFC5869TestCase1() {
        // Given
        byte[] inputKeyMaterial = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = hexToBytes("000102030405060708090a0b0c");
        byte[] info = hexToBytes("f0f1f2f3f4f5f6f7f8f9");
        int outputKeySize = 42;

        // When
        HKDFKeyGenerator.HKDFMasterKey masterKey = HKDFKeyGenerator.extractMasterKey(inputKeyMaterial, salt);
        Key derivedKey = HKDFKeyGenerator.expandKey(masterKey, "AES", info, outputKeySize);

        // Then
        assertThat(masterKey.getEncoded()).isEqualTo(hexToBytes(
                "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"));
        assertThat(derivedKey.getEncoded()).isEqualTo(hexToBytes(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"));
        assertThat(derivedKey.getAlgorithm()).isEqualTo("AES");
    }

    @Test
    public void shouldPassRFC5869TestCase2() {
        // Given
        byte[] inputKeyMaterial = hexToBytes(
                  "000102030405060708090a0b0c0d0e0f"
                + "101112131415161718191a1b1c1d1e1f"
                + "202122232425262728292a2b2c2d2e2f"
                + "303132333435363738393a3b3c3d3e3f"
                + "404142434445464748494a4b4c4d4e4f");
        byte[] salt = hexToBytes(
                  "606162636465666768696a6b6c6d6e6f"
                + "707172737475767778797a7b7c7d7e7f"
                + "808182838485868788898a8b8c8d8e8f"
                + "909192939495969798999a9b9c9d9e9f"
                + "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
        byte[] info = hexToBytes(
                  "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"
                + "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf"
                + "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf"
                + "e0e1e2e3e4e5e6e7e8e9eaebecedeeef"
                + "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");
        int outputKeySize = 82;


        // When
        HKDFKeyGenerator.HKDFMasterKey masterKey = HKDFKeyGenerator.extractMasterKey(inputKeyMaterial, salt);
        Key derivedKey = HKDFKeyGenerator.expandKey(masterKey, "AES", info, outputKeySize);

        // Then
        assertThat(masterKey.getEncoded()).isEqualTo(hexToBytes(
                "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244"));
        assertThat(derivedKey.getEncoded()).isEqualTo(hexToBytes(
                  "b11e398dc80327a1c8e7f78c596a4934"
                + "4f012eda2d4efad8a050cc4c19afa97c"
                + "59045a99cac7827271cb41c65e590e09"
                + "da3275600c2f09b8367793a9aca3db71"
                + "cc30c58179ec3e87c14c01d5c1f3434f"
                + "1d87"));
        assertThat(derivedKey.getAlgorithm()).isEqualTo("AES");
    }

    @Test
    public void shouldPassRFC5869TestCase3() {
        // Given
        byte[] inputKeyMaterial = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = new byte[0];
        byte[] info = new byte[0];
        int outputKeySize = 42;

        // When
        HKDFKeyGenerator.HKDFMasterKey masterKey = HKDFKeyGenerator.extractMasterKey(inputKeyMaterial, salt);
        Key derivedKey = HKDFKeyGenerator.expandKey(masterKey, "AES", info, outputKeySize);

        // Then
        assertThat(masterKey.getEncoded()).isEqualTo(hexToBytes(
                "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04"));
        assertThat(derivedKey.getEncoded()).isEqualTo(hexToBytes(
                "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"));
        assertThat(derivedKey.getAlgorithm()).isEqualTo("AES");
    }


    @DataProvider
    public static Object[][] weakKeyMaterial() {
        final Object[][] result = new Object[16][1];
        for (int i = 0; i < 16; ++i) {
            result[i][0] = new byte[i];
        }
        return result;
    }

    private static byte[] hexToBytes(String input) {
        return DatatypeConverter.parseHexBinary(input);
    }
}