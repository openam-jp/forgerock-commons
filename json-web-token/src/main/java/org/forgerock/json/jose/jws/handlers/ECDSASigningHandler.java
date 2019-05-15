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
 * Portions Copyrighted 2019 Open Source Solution Technology Corporation
 */

package org.forgerock.json.jose.jws.handlers;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import org.forgerock.json.jose.exceptions.JwsException;
import org.forgerock.json.jose.exceptions.JwsSigningException;
import org.forgerock.json.jose.jws.JwsAlgorithm;
import org.forgerock.json.jose.jws.JwsAlgorithmType;
import org.forgerock.json.jose.jws.SupportedEllipticCurve;
import org.forgerock.json.jose.utils.DerUtils;
import org.forgerock.json.jose.utils.Utils;
import org.forgerock.util.Reject;

/**
 * Elliptic Curve Digital Signature Algorithm (ECDSA) signing and verification.
 */
public class ECDSASigningHandler implements SigningHandler {
    private final ECPrivateKey signingKey;
    private final ECPublicKey verificationKey;
    private final SupportedEllipticCurve curve;

    /**
     * Constructs the ECDSA signing handler for signing only.
     *
     * @param signingKey the private key to use for signing. Must not be null.
     */
    public ECDSASigningHandler(final ECPrivateKey signingKey) {
        this.signingKey = signingKey;
        this.verificationKey = null;
        this.curve = validateKey(signingKey);
    }

    /**
     * Constructs the ECDSA signing handler for verification only.
     *
     * @param verificationKey the public key to use for verification. Must not be null.
     */
    public ECDSASigningHandler(final ECPublicKey verificationKey) {
        this.signingKey = null;
        this.verificationKey = verificationKey;
        this.curve = validateKey(verificationKey);
    }

    @Override
    public byte[] sign(final JwsAlgorithm algorithm, final String data) {
        return sign(algorithm, data.getBytes(Utils.CHARSET));
    }

    @Override
    public byte[] sign(final JwsAlgorithm algorithm, final byte[] data) {
        validateAlgorithm(algorithm);

        try {
            final Signature signature = Signature.getInstance(algorithm.getAlgorithm());
            signature.initSign(signingKey);
            signature.update(data);
            return DerUtils.decode(signature.sign(), curve.getSignatureSize());
        } catch (SignatureException | InvalidKeyException e) {
            throw new JwsSigningException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new JwsSigningException("Unsupported Signing Algorithm, " + algorithm.getAlgorithm(), e);
        }
    }

    @Override
    public boolean verify(final JwsAlgorithm algorithm, final byte[] data, final byte[] signature) {
        validateAlgorithm(algorithm);

        try {
            final Signature validator = Signature.getInstance(algorithm.getAlgorithm());
            validator.initVerify(verificationKey);
            validator.update(data);
            SupportedEllipticCurve curve = SupportedEllipticCurve.forSignature(signature);
            return validator.verify(DerUtils.encode(signature, curve.getSignatureSize()));
        } catch (SignatureException | InvalidKeyException e) {
            throw new JwsSigningException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new JwsSigningException("Unsupported Signing Algorithm, " + algorithm.getAlgorithm(), e);
        }
    }

    private void validateAlgorithm(JwsAlgorithm algorithm) {
        Reject.ifNull(algorithm, "Algorithm must not be null.");
        Reject.ifTrue(algorithm.getAlgorithmType() != JwsAlgorithmType.ECDSA, "Not an ECDSA algorithm.");
    }

    /**
     * Validate that the parameters of the key match the standard P-256 curve as required by the ES256 JWA standard.
     * @param key the key to validate.
     */
    private SupportedEllipticCurve validateKey(final ECKey key) {
        Reject.ifNull(key);
        try {
            return SupportedEllipticCurve.forKey(key);
        } catch (IllegalArgumentException ex) {
            throw new JwsException(ex);
        }
    }
}
