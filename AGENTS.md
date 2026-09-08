# API Test Suite Contributor Guide

## Purpose

This repository contains automated API tests. Tests use synthetic, versioned,
deterministic data so that results are reproducible and independent of
production data.

## Project Structure

- This is one Gradle Java project, not a multi-project build.
- Keep tests for each API in that API's dedicated package. Place API-specific
  request builders, assertions, and fixtures alongside the API they support.
- Put code in a shared utility package only when it has a clear, stable use
  across multiple APIs. Do not create cross-package dependencies merely to
  reuse a small implementation detail.

## Test Design

- Write tests as readable specifications of observable behavior. Use
  descriptive, behavior-oriented test names and a clear Arrange--Act--Assert
  flow.
- Each test should verify one behavior. Keep its setup, action, and assertion
  close together so its intent is apparent without following control flow
  elsewhere.
- Prefer precise domain names and small, single-purpose helpers to explanatory
  comments. Use comments only to preserve non-obvious intent or constraints.
- Do not rely on execution order, another test's output, mutable shared
  environments, production data, or arbitrary timing delays.
- Create only the state required by the scenario. Reset the database to its
  defined baseline before every test, then load only the fixtures needed by the
  scenario. Keep fixtures minimal and name them after the scenario they
  represent.
- Make failures useful: assertions should identify the endpoint, scenario, and
  relevant mismatch.

## Test Tooling

- Use JUnit Jupiter for test execution and lifecycle setup/teardown.
- Use Testcontainers to start dependent services in isolated, reproducible
  test environments.
- Use REST Assured for HTTP requests and response verification.
- Use AssertJ and JsonUnit for focused assertions, especially semantic JSON
  content checks.

## Container Lifecycle and Isolation

- Give each API package an independent test environment. When the API has a
  database dependency, use one private Docker network, one database container,
  and one container running that API image. Stateless APIs may start only the
  API image.
- Start the API and database once per API package's top-level integration test
  suite (for example in `@BeforeAll`) and stop them after the suite. Use nested
  scenario classes to group tests that share that environment.
- Initialize the database schema and migrations once when its container
  starts. Before every test, truncate application tables, reset generated
  identifiers as required by the database, and load that test's minimal
  fixtures.
- Run test methods that share an API environment sequentially. Parallelize
  only independent API packages, each with its own API and database
  containers, and only within available CI capacity.
- Configure the API to reach the database through its Docker network alias;
  expose only the API's mapped port to the test JVM. Pin container image
  versions and wait for a real readiness or health endpoint.
- Do not use Testcontainers reusable containers in CI. They may be enabled as
  an opt-in local-development speed optimization only.

## Code Quality

- Keep classes and methods small, cohesive, and focused on one responsibility.
- Extract a shared abstraction only once its common purpose is clear; avoid
  generic utility methods and stateful base test classes.
- A shared environment helper is acceptable only when it represents a stable,
  cross-API lifecycle contract; do not use inheritance to hide test state.
- Prefer local setup to broad shared setup unless the shared setup makes every
  affected test clearer.
- Leave touched code cleaner than it was, without expanding the change beyond
  the task.

## Before Handoff

- Run the relevant Gradle tests for the changed package(s).
- Keep changes scoped to the requested behavior and include any required test
  data with the tests that use it.
