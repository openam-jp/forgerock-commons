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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.testng.annotations.Test;

/**
 * Abstract base-class for JMH benchmarks, which configures default settings.
 * <p>
 * References:
 * <ul>
 * <li><a href="http://java-performance.info/jmh/">Introduction to JMH</a></li>
 * <li><a href="http://openjdk.java.net/projects/code-tools/jmh/">OpenJDK: jmh</a></li>
 * </ul>
 */
@Warmup(iterations = BenchmarkBase.DEFAULT_WARMUP_ITERATIONS)
@Measurement(iterations = BenchmarkBase.DEFAULT_MEASURE_ITERATIONS)
@Fork(value = BenchmarkBase.DEFAULT_FORKS)
@Threads(BenchmarkBase.DEFAULT_THREADS)
@State(Scope.Benchmark)
public abstract class BenchmarkBase {

    /** Default number of concurrent threads to run per benchmark (4). */
    protected static final int DEFAULT_THREADS = 4;

    /** Default number of forks to run per benchmark (2). */
    protected static final int DEFAULT_FORKS = 2;

    /** Default number of warmup iterations to run per benchmark (10). */
    protected static final int DEFAULT_WARMUP_ITERATIONS = 10;

    /** Default number of measurement iterations to run per benchmark (10). */
    protected static final int DEFAULT_MEASURE_ITERATIONS = 10;

    /**
     * Runs the JMH benchmark test.
     *
     * @throws Exception when an error occurs
     */
    @Test
    public void run() throws Exception {
        new Runner(newOptionsBuilder().build()).run();
    }

    /**
     * Creates a JMH options-builder, with default options and command-line overrides.
     *
     * @return JMH options-builder
     * @throws IOException error creating report file
     */
    protected ChainedOptionsBuilder newOptionsBuilder() throws IOException {
        final String className = getClass().getSimpleName();
        final ChainedOptionsBuilder options = new OptionsBuilder()
                .include(".*" + className + ".*")
                .jvmArgs(jvmArgs());
        if (getTempDir() != null) {
            options.jvmArgsAppend("-Djava.io.tmpdir=" + getTempDir());
        }
        if (getWarmupIterations() > 0) {
            options.warmupIterations(getWarmupIterations());
        }
        if (getMeasureIterations() > 0) {
            options.measurementIterations(getMeasureIterations());
        }
        if (getForks() > 0) {
            options.forks(getForks());
        }
        if (getThreads() > 0) {
            options.threads(getThreads());
        }
        if (getReportDir() != null) {
            final Path dirPath = Paths.get(getReportDir());
            final Path filePath = dirPath.resolve(className + ".json");
            if (!Files.deleteIfExists(filePath)) {
                Files.createDirectories(dirPath);
            }
            options.resultFormat(ResultFormatType.JSON);
            options.result(filePath.toAbsolutePath().toString());
        }
        return options;
    }

    /**
     * Gets the default JVM arguments to use per test.
     *
     * @return default JVM arguments
     */
    protected String[] jvmArgs() {
        return new String[]{
            "-server", "-dsa", "-da", "-XX:+AggressiveOpts", "-XX:+UseBiasedLocking",
            "-XX:+UseFastAccessorMethods", "-XX:+OptimizeStringConcat", "-XX:+HeapDumpOnOutOfMemoryError"
        };
    }

    /**
     * Gets number of warmup iterations to run per benchmark (default 10), from system property argument
     * {@code -DwarmupIterations=10}.
     *
     * @return Number of warmup iterations to run per benchmark or {@code -1}
     */
    protected int getWarmupIterations() {
        return getIntProperty("warmupIterations", -1);
    }

    /**
     * Gets number of measurement iterations to run per benchmark (default 10), from system property argument
     * {@code -DmeasureIterations=10}.
     *
     * @return Number of measurement iterations to run per benchmark or {@code -1}
     */
    protected int getMeasureIterations() {
        return getIntProperty("measureIterations", -1);
    }

    /**
     * Gets number of forks to run per benchmark (default 2), from system property argument {@code -Dforks=2}.
     *
     * @return Number of forks to run per benchmark or {@code -1}
     */
    protected int getForks() {
        return getIntProperty("forks", -1);
    }

    /**
     * Gets number of concurrent threads to use per benchmark (default 4), from system property argument
     * {@code -Dthreads=4}.
     *
     * @return Number of concurrent threads to use per benchmark or {@code -1}
     */
    protected int getThreads() {
        return getIntProperty("threads", -1);
    }

    /**
     * Gets directory to output JMH report, from system property argument {@code -DperfReportDir=/myDir}.
     * <p>
     * The default directory is defined in the Maven POM as {@code ${project.build.directory}/reports/performance/}.
     *
     * @return JMH report directory
     */
    protected String getReportDir() {
        return System.getProperty("perfReportDir");
    }

    /**
     * Gets custom JVM temp directory, from system property argument {@code -DtempDir=/myTempDir}.
     *
     * @return JVM temp dir
     */
    protected String getTempDir() {
        return System.getProperty("tempDir");
    }

    private int getIntProperty(final String name, final int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(name));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
