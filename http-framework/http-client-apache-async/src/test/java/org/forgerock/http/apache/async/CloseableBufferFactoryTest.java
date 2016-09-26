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

package org.forgerock.http.apache.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CloseableBufferFactoryTest {

    @Test
    public void shouldPreallocateBuffers() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        CloseableBufferFactory<ByteBuffer> buffers = countingBufferFactory(counter, 3);
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    public void shouldReleaseBufferToThePool() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        CloseableBufferFactory<ByteBuffer> buffers = countingBufferFactory(counter, 1);
        // Only 1 pre-allocated buffer
        assertThat(counter.get()).isEqualTo(1);

        // Consume the pre-allocated buffer : no new allocation
        CloseableBufferFactory<ByteBuffer>.CloseableBuffer buffer1 = buffers.newInstance();
        assertThat(counter.get()).isEqualTo(1);

        // Release the buffer into the pool
        buffer1.close();

        // Request a new instance : no new allocations should happen
        buffers.newInstance();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    public void shouldAllocateNewBuffersWhenPoolIsEmpty() throws Exception {
        final AtomicInteger counter = new AtomicInteger(0);
        CloseableBufferFactory<ByteBuffer> buffers = countingBufferFactory(counter, 1);
        // Only 1 pre-allocated buffer
        assertThat(counter.get()).isEqualTo(1);

        // Consume the pre-allocated buffer : no new allocation
        CloseableBufferFactory<ByteBuffer>.CloseableBuffer buffer1 = buffers.newInstance();
        assertThat(counter.get()).isEqualTo(1);

        // That will create another buffer
        CloseableBufferFactory<ByteBuffer>.CloseableBuffer buffer2 = buffers.newInstance();
        assertThat(counter.get()).isEqualTo(2);
    }

    private static CloseableBufferFactory<ByteBuffer> countingBufferFactory(final AtomicInteger counter,
            final int poolInitialSize) {
        return new CloseableBufferFactory<ByteBuffer>(poolInitialSize) {
            @Override
            protected ByteBuffer allocate() {
                counter.getAndIncrement();
                return ByteBuffer.allocate(128);
            }
        };
    }
}
