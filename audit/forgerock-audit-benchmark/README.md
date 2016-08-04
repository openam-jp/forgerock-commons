# Audit Benchmarks

### Description

The `forgerock-audit-benchmark` module runs [JMH](http://openjdk.java.net/projects/code-tools/jmh/)
benchmarks that are bootstrapped from unit test classes. During builds, test do **not** run by default,
because their execution time may be lengthy and temporary files generated may be large. The results
from each test class are printed to the command-line and written to a JSON file, which could be
processed to detect performance improvements/regressions.

Not that the JVM's temporary directory is used to store all generated files, and that these
files/directories are deleted when a test completes. Cleanup may fail if an error occurs during
execution of the tests.

### Running the Tests

To run all tests, with default settings,

```
cd ./audit/forgerock-audit-benchmark

mvn clean test -DskipTests=false
```

Run a single test with,

```
mvn clean test -DskipTests=false -Dtest=CsvAuditEventHandlerWriteBenchmarkTest
```

### Command-Line Options

The following command-line options are passed to the JMH runtime, which instantiates its own JVM
instances.

Option                   | Description
------------------------ | -------------------
`-DwarmupIterations=10`  | Number of warmup iterations to run per benchmark (default 10)
`-DmeasureIterations=10` | Number of measurement iterations to run per benchmark (default 10)
`-Dforks=2`              | Number of forks to run per benchmark (default 2)
`-Dthreads=4`            | Number of concurrent threads to use per benchmark (default 4)
`-DperfReportDir=/myDir` | Directory to output JMH report named `[unitTestClassName].json`. The default output directory is defined in the Maven POM as `${project.build.directory}/reports/performance/`.
`-DtempDir=/myTempDir`   | Custom JVM temp directory.

