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

package org.forgerock.audit.benchmark;

import static org.forgerock.json.JsonValue.json;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.forgerock.audit.events.EventTopicsMetaData;
import org.forgerock.audit.events.handlers.AuditEventHandler;
import org.forgerock.audit.events.handlers.EventHandlerConfiguration;
import org.forgerock.json.JsonValue;
import org.forgerock.util.test.FileUtils;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Base-state for JMH benchmarks, which subclasses extend in order to supply a
 * {@link #updateConfiguration(T) updateConfiguration} for different
 * benchmark scenarios of an {@link AuditEventHandler}.
 *
 * @param <T> Configuration class for {@link AuditEventHandler} under test.
 */
public abstract class AuditEventHandlerBenchmarkState<T extends EventHandlerConfiguration> {
    private Path logDirectoryPath;

    /** {@link AuditEventHandler} instance under test. */
    protected AuditEventHandler handler;

    /**
     * Builds base configuration, which can be customized by overriding
     * {@link #updateConfiguration(EventHandlerConfiguration)}.
     *
     * @return Base configuration
     */
    public abstract T buildBaseConfiguration();

    /**
     * Subclasses should override this method in order to customize the base-configuration.
     *
     * @param configuration Base-configuration
     */
    protected void updateConfiguration(final T configuration) {
        // empty
    }

    /**
     * Subclasses should override this method in order to interact with the handler after
     * {@link AuditEventHandler#startup()}, but before the benchmarks start (e.g., populate with data before
     * read-benchmarks).
     *
     * @throws Exception when an error occurs
     */
    protected void afterStartup() throws Exception {
        // empty
    }

    /**
     * Builds the {@link AuditEventHandler} instance.
     *
     * @param configuration Configuration
     * @return {@link AuditEventHandler} instance
     * @throws Exception when an error occurs
     */
    public abstract AuditEventHandler buildAuditEventHandler(T configuration) throws Exception;

    /**
     * Configures and starts {@link #handler}.
     *
     * @throws Exception when an error occurs
     */
    @Setup(Level.Trial)
    public void beforeTrial() throws Exception {
        logDirectoryPath = Files.createTempDirectory(AuditEventHandlerBenchmarkState.class.getSimpleName());

        final T configuration = buildBaseConfiguration();
        updateConfiguration(configuration);

        handler = buildAuditEventHandler(configuration);
        handler.startup();
        afterStartup();
    }

    /**
     * Shuts-down {@link #handler} and deletes temporary files.
     *
     * @throws Exception when an error occurs
     */
    @TearDown(Level.Trial)
    public void afterTrial() throws Exception {
        try {
            handler.shutdown();
        } finally {
            FileUtils.deleteRecursively(logDirectoryPath);
        }
    }

    /**
     * Returns absolute-path to temporary log-directory.
     *
     * @return Temporary log-directory
     */
    protected String getLogDirectory() {
        return logDirectoryPath.toAbsolutePath().toString();
    }

    /**
     * Loads {@link EventTopicsMetaData} from classpath resource.
     *
     * @param resourcePath Events JSON file path on classpath (should start with a forward-slash)
     * @return {@link EventTopicsMetaData} instance
     * @throws Exception when an error occurs
     */
    protected EventTopicsMetaData getEventTopicsMetaData(final String resourcePath) throws Exception {
        Map<String, JsonValue> events = new LinkedHashMap<>();
        try (final InputStream configStream = getClass().getResourceAsStream(resourcePath)) {
            final JsonValue predefinedEventTypes = json(new ObjectMapper().readValue(configStream, Map.class));
            for (String eventTypeName : predefinedEventTypes.keys()) {
                events.put(eventTypeName, predefinedEventTypes.get(eventTypeName));
            }
        }
        return new EventTopicsMetaData(events);
    }
}
