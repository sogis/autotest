# API Test Suite

This project runs reproducible API integration tests against synthetic data.
Tests are written in Java with JUnit Jupiter and run through the Gradle Wrapper.

## Prerequisites

Install the following before running the suite:

- Git
- JDK 25
- A running Docker daemon that your user can access
- Internet access on the first run, so Gradle can download its distribution and
  dependencies and Testcontainers can pull required images

Confirm that the expected Java version and Docker access are available:

```sh
java -version
docker version
```

The build is configured for Java 25. Set `JAVA_HOME` and your `PATH` so that
`java -version` reports JDK 25 before invoking Gradle.

## Run tests

Always use the checked-in Gradle Wrapper from the repository root:

```sh
./gradlew test
```

Run one test class:

```sh
./gradlew test --tests 'ch.so.agi.autotest.tests.httpbin.HttpbinTest'
```

Run all test classes in an API package and any of its subpackages:

```sh
./gradlew test --tests 'ch.so.agi.autotest.tests.httpbin.*'
```

Replace `ch.so.agi.autotest.tests.httpbin` with the package for the API under test.

## Test environment

The suite starts its dependent services with Testcontainers. For example, the
current HTTPBin tests pull and start the pinned
`ghcr.io/mccutchen/go-httpbin:2.25.0` image, wait until its HTTP endpoint is
ready, and connect through a dynamically mapped local port.

You do not need to start the API container manually or reserve a local port.
Docker must remain available for the full test run.

## Results and troubleshooting

Run with option --PhttpTraffic to output the http traffic and the 
log of the container under test.

```sh
./gradlew test -PhttpTraffic
```

After a run, open the HTML report at:

```text
build/reports/tests/test/index.html
```

For more diagnostic output, rerun the failing command with one of these
options:

```sh
./gradlew test --info
./gradlew test --stacktrace
```

Common setup failures:

- **Unsupported Java version:** install JDK 25, then update `JAVA_HOME` and
  `PATH` so the shell resolves that JDK.
- **Docker daemon unavailable or permission denied:** start Docker and ensure
  your user has permission to access its socket before retrying `docker
  version`.
- **Gradle or image download fails on the first run:** restore network access
  to Gradle's distribution/dependency repositories and the container-image
  registry, then rerun the same command.
