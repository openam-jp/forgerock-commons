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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FileBranchingStreamTest {

    private File file;

    @BeforeClass
    public void setup() throws Exception {
        file = File.createTempFile("unit.", ".test");
        file.deleteOnExit();
        Files.write(file.toPath(), "A test file".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testRead() throws Exception {
        try (BranchingInputStream stream = new FileBranchingStream(file)) {
            assertThat(stream.read()).isEqualTo('A');
            assertThat(stream.read()).isEqualTo(' ');
        }
    }

    @Test
    public void testCopy() throws Exception {
        try (BranchingInputStream stream = new FileBranchingStream(file)) {
            assertThat(stream.read()).isEqualTo('A');
            try (BranchingInputStream child = stream.branch();
                 BranchingInputStream sibling = child.copy()) {
                assertThat(sibling.read()).isSameAs(stream.read()).isSameAs(child.read()).isEqualTo(' ');
                assertThat(sibling.parent()).isSameAs(child.parent());
            }
        }
    }

    @Test
    public void testParent() throws Exception {
        try (BranchingInputStream stream = new FileBranchingStream(file)) {
            assertThat(stream.read()).isEqualTo('A');
            try (BranchingInputStream child = stream.branch()) {
                assertThat(child.read()).isSameAs(stream.read()).isEqualTo(' ');
                assertThat(child.parent()).isSameAs(stream);
            }
        }
    }
}