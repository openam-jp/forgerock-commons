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

package org.forgerock.util.test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utilities for working with files within unit tests.
 */
public final class FileUtils {

    private FileUtils() {
        // hidden constructor
    }

    /**
     * Recursively deletes one or more files and/or directories. If an {@link IOException} is thrown while traversing
     * a path, the error message will be recorded and a new {@link IOException} thrown after traversing all other
     * paths.
     *
     * @param paths Files and/or directories to delete
     * @throws IOException failure to delete a path
     */
    public static void deleteRecursively(final Path... paths) throws IOException {
        StringBuilder exceptionMessages = null;
        for (final Path path : paths) {
            try {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file,
                            final BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir,
                            final IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (final IOException e) {
                // store the message, so that we can continue deleting paths
                if (exceptionMessages == null) {
                    exceptionMessages = new StringBuilder();
                    exceptionMessages.append(e.getMessage());
                } else {
                    exceptionMessages.append(", ").append(e.getMessage());
                }
            }
        }
        if (exceptionMessages != null) {
            throw new IOException("Failed to delete one or more paths: " + exceptionMessages);
        }
    }

}