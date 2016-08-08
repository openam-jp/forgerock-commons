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

package org.forgerock.http.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.Utils.closeSilently;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.forgerock.util.test.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test implementations of {@link Buffer}.
 */
public class BufferTest {

    private static final int BUF_SIZE = 2;
    private static Path tempDir;

    @BeforeClass
    public static void beforeClass() throws IOException {
        tempDir = Files.createTempDirectory(BufferTest.class.getSimpleName());
    }

    @AfterClass
    public static void afterClass() throws IOException {
        FileUtils.deleteRecursively(tempDir);
    }

    @DataProvider
    public Object[][] data() throws IOException {
        return new Object[][]{
                {new FileBuffer(Files.createTempFile(tempDir, "buf", "tmp").toFile(), BUF_SIZE)},
                {new MemoryBuffer(BUF_SIZE, BUF_SIZE)},
                {new TemporaryBuffer(BUF_SIZE, BUF_SIZE, BUF_SIZE, tempDir.toFile())}
        };
    }

    @Test(dataProvider = "data", expectedExceptions = IndexOutOfBoundsException.class)
    public void singleByteIndexOutOfBoundsExceptionTest(final Buffer buffer) throws IOException {
        try {
            buffer.read(BUF_SIZE);
        } finally {
            closeSilently(buffer);
        }
    }

    @Test(dataProvider = "data")
    public void singleByteWriteReadTest(final Buffer buffer) throws IOException {
        final byte b = 1;
        try {
            buffer.append(b);

            assertThat(buffer.length()).isEqualTo(1);
            assertThat(buffer.read(0)).isEqualTo(b);
        } finally {
            closeSilently(buffer);
        }
    }

    @Test(dataProvider = "data")
    public void multiByteWriteReadTest(final Buffer buffer) throws IOException {
        final byte[] input = new byte[] {1, 1};
        final byte[] output = new byte[input.length];
        try {
            buffer.append(input, 0, input.length);

            assertThat(buffer.length()).isEqualTo(input.length);
            assertThat(buffer.read(0, output, 0, input.length)).isEqualTo(input.length);
            assertThat(output).isEqualTo(input);
            assertThat(buffer.read(input.length, output, 0, input.length)).isEqualTo(0);
        } finally {
            closeSilently(buffer);
        }
    }
}
