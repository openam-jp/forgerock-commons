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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.http.apache.async;

import java.io.Closeable;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.forgerock.util.Factory;

/**
 * A {@link Factory} of a pool of preallocated NIO {@link Buffer} exposed as instances of {@link Closeable}.
 * The method {@link CloseableBuffer#close()} releases the instance into the pool.
 * The pool will grow in size up to the maximum concurrent threads that call an instance of the {@link Factory}.
 *
 * It can be used like this :
 * {@code
 * CloseableBufferFactory<ByteBuffer> closeableByteBufferFactory = new CloseableBufferFactory<ByteBuffer>(threadCount) {
 *     @Override
 *     protected java.nio.ByteBuffer allocate() {
 *         return ByteBuffer.allocate(8 * 1_024);
 *     }
 * };
 * try (CloseableBufferFactory.CloseableBuffer<ByteBuffer> buffer = closeableByteBufferFactory.newInstance()) {
 *     ByteBuffer byteBuffer = buffer.getBuffer();
 *     // Use the byteBuffer
 * }
 * }
 */
abstract class CloseableBufferFactory<T extends Buffer> implements
        Factory<CloseableBufferFactory<T>.CloseableBuffer> {

    /**
     * Fluent method the create a CloseableBufferFactory<ByteBuffer>.
     * @param poolInitialSize the initial size of the pool
     * @param bufferSize the size of the buffer
     * @return an instance of CloseableBufferFactory that will handle some {@link ByteBuffer}.
     */
    static CloseableBufferFactory<ByteBuffer> closeableByteBufferFactory(final int poolInitialSize,
            final int bufferSize) {
        return new CloseableBufferFactory<ByteBuffer>(poolInitialSize) {
            @Override
            protected java.nio.ByteBuffer allocate() {
                return ByteBuffer.allocate(bufferSize);
            }
        };
    }

    /**
     * Pool of pre-allocated {@code T} instances, which will grow in size up to the maximum concurrent
     * threads that call this class.
     */
    private final Queue<T> pool;

    CloseableBufferFactory(int poolInitialSize) {
        pool = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < poolInitialSize; ++i) {
            pool.add(allocate());
        }
    }

    /**
     * Any implementation of this class will have to define this method in which the real buffer is allocated.
     * Here are some examples :
     * {@code
     * @Override
     * protected java.nio.CharBuffer allocate() {
     *     return CharBuffer.allocate(1_024);
     * }
     * }
     * or
     * {@code
     * @Override
     * protected java.nio.ByteBuffer allocate() {
     *     return ByteBuffer.allocateDirect(4_096);
     * }
     * }
     * @return
     */
    protected abstract T allocate();

    @Override
    public final CloseableBuffer newInstance() {
        T instance = pool.poll();
        if (instance == null) {
            instance = allocate();
        }
        return new CloseableBuffer(instance);
    }

    final class CloseableBuffer implements AutoCloseable {

        private final T buffer;

        private CloseableBuffer(T buffer) {
            this.buffer = buffer;
        }

        @Override
        public void close() {
            buffer.clear();
            pool.add(buffer);
        }

        T getBuffer() {
            return buffer;
        }
    }
}

