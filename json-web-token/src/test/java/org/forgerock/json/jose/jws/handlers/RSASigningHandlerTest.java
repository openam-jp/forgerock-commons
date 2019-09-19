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
 * Copyright 2019 Open Source Solution Technology Corporation
 */

package org.forgerock.json.jose.jws.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.forgerock.json.jose.helper.KeysHelper;
import org.forgerock.json.jose.jws.JwsAlgorithm;
import org.forgerock.json.jose.jws.SigningManager;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RSASigningHandlerTest {

    @Test(dataProvider = "supportedAlgorithms")
    public void shouldEncodeSignatureCorrectly(JwsAlgorithm algorithm) throws Exception {
        // Given
        SigningHandler signingHandler = new SigningManager().newRsaSigningHandler(KeysHelper.getRSAPrivateKey());
        final byte[] data = "Sample Message".getBytes(StandardCharsets.UTF_8);
        int expectedSize = KeysHelper.getRSAPrivateKey().getModulus().bitLength() / 8;

        // When
        final byte[] signature = signingHandler.sign(algorithm, data);

        // Then
        assertThat(signature).hasSize(expectedSize);
    }

    @Test(dataProvider = "supportedAlgorithms")
    public void shouldVerifyCorrectly(JwsAlgorithm algorithm) throws Exception {
        // Given
        SigningHandler signingHandler = new SigningManager().newRsaSigningHandler(KeysHelper.getRSAPrivateKey());
        SigningHandler verificationHandler = new SigningManager().newRsaSigningHandler(KeysHelper.getRSAPublicKey());
        final byte[] data = "Sample Message".getBytes(StandardCharsets.UTF_8);
        final byte[] signature = signingHandler.sign(algorithm, data);

        // When
        boolean valid = verificationHandler.verify(algorithm, data, signature);

        // Then
        assertThat(valid).isTrue();
    }

    @Test(dataProvider = "supportedAlgorithms")
    public void shouldDetectTampering(JwsAlgorithm algorithm) throws Exception {
        // Given
        SigningHandler signingHandler = new SigningManager().newRsaSigningHandler(KeysHelper.getRSAPrivateKey());
        SigningHandler verificationHandler = new SigningManager().newRsaSigningHandler(KeysHelper.getRSAPublicKey());
        final byte[] data = "Sample Message".getBytes(StandardCharsets.UTF_8);
        final byte[] signature = signingHandler.sign(algorithm, data);

        data[0] = 0; // Make a change to the data

        // When
        boolean valid = verificationHandler.verify(algorithm, data, signature);

        // Then
        assertThat(valid).isFalse();
    }

    @DataProvider
    public static Object[][] supportedAlgorithms () {
        return new Object[][] {
                { JwsAlgorithm.RS256 },
                { JwsAlgorithm.RS384 },
                { JwsAlgorithm.RS512 }
        };
    }
}
