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
 * Copyright 2013-2015 ForgeRock AS.
 * Portions Copyrighted 2019 OGIS-RI Co., Ltd.
 * Portions Copyrighted 2023 OSSTech Corporation
 */

package org.forgerock.json.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.SignatureException;

import javax.xml.bind.DatatypeConverter;

import org.forgerock.json.jose.utils.DerUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class DerUtilsTest {

    @DataProvider(name = "lengthReadWriteContext")
    private Object[][] getLengthReadWriteContextData() {
		return new Object[][] {
			{0, "0000000000000000"},
			{1, "0100000000000000"},
			{127, "7F00000000000000"},
			{128, "8180000000000000"},
			{256, "8201000000000000"},
			{65535, "82FFFF0000000000"},
			{12345678, "83BC614E00000000"},
		};
	}

	@Test(dataProvider = "lengthReadWriteContext")
	public void writeLengthTest(int len, String expected) {
		ByteBuffer bb = ByteBuffer.allocate(8);

		DerUtils.writeLength(bb, len);
		assertThat(DatatypeConverter.printHexBinary(bb.array())).isEqualTo(expected);
	}

	@Test(dataProvider = "lengthReadWriteContext")
	public void readLengthTest(int expectedLength, String byteSequence) {
		byte[] elements = DatatypeConverter.parseHexBinary(byteSequence);
		ByteBuffer bb = ByteBuffer.wrap(elements);

		int len = DerUtils.readLength(bb);
		assertThat(len).isEqualTo(expectedLength);
	}

    @DataProvider(name = "derEncodeContext")
    private Object[][] getDerEncodeContextData() {
		return new Object[][] {
			{"0000", "3006020100020100"},
			{"00FF", "3007020100020200FF"},
			{"FF00", "3007020200FF020100"},
			{"FFFF", "3008020200FF020200FF"},
			{"00000000", "3006020100020100"},
			{"0000FFFF", "3008020100020300FFFF"},
			{"FFFF0000", "3008020300FFFF020100"},
			{"FFFFFFFF", "300A020300FFFF020300FFFF"},
			// "43657274..." is the hexadecimal representation of "Certificate: Data: Version: 3 (0x2) Serial Number:".
			{"43657274696669636174653A20446174613A2056657273696F6E3A20332028307832292053657269616C204E756D6265723A", "3036021943657274696669636174653A20446174613A2056657273696F02196E3A20332028307832292053657269616C204E756D6265723A"}
		};
	}

	@Test(expectedExceptions={java.lang.NullPointerException.class})
	public void encodeNullTest() {
		DerUtils.encode(null, 0);
	}

	@Test(dataProvider = "derEncodeContext")
	public void encodeTest(String targetHex, String expectedHex) {
		byte[] result = DerUtils.encode(DatatypeConverter.parseHexBinary(targetHex), targetHex.length() / 2);
		assertThat(DatatypeConverter.printHexBinary(result)).isEqualTo(expectedHex);
	}

	@Test(expectedExceptions={java.lang.NullPointerException.class, SignatureException.class})
	public void decodeNullTest()
			throws SignatureException {
		DerUtils.decode(null, 0);
	}

	@Test(dataProvider = "derEncodeContext")
	public void decodeTest(String expectedHex, String targetHex)
			throws SignatureException, UnsupportedEncodingException {
		byte[] result = DerUtils.decode(DatatypeConverter.parseHexBinary(targetHex), expectedHex.length() / 2);
		assertThat(DatatypeConverter.printHexBinary(result)).isEqualTo(expectedHex);
	}
}
