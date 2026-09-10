package ch.so.agi.autotest.util;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Consumer;

public final class PostgisContainers {

    private static final String DATABASE_NAME = "autotest";
    private static final String USERNAME = "autotest";
    private static final String PASSWORD = "autotest";

    private PostgisContainers() {
    }

    public static PostgreSQLContainer provision(Network network, String... schemas) {
        return provision(network, null, schemas);
    }

    public static PostgreSQLContainer provision(Network network, Consumer<OutputFrame> logConsumer,
        String... schemas) {
        validateSchemaNames(schemas);
        PostgreSQLContainer database = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5-alpine")
                .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName(DATABASE_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .waitingFor(Wait.forSuccessfulCommand(
                "pg_isready -h 127.0.0.1 -U %s -d %s".formatted(USERNAME, DATABASE_NAME))
                .withStartupTimeout(Duration.ofSeconds(120)));
        if (logConsumer != null) {
            database.withLogConsumer(logConsumer);
        }

        try {
            database.start();
            initialize(database, schemas);
            return database;
        }
        catch (RuntimeException | Error failure) {
            try {
                database.stop();
            }
            catch (RuntimeException stopFailure) {
                failure.addSuppressed(stopFailure);
            }
            throw failure;
        }
    }

    public static void ensureSchema(PostgreSQLContainer database, String schema) {
        validateSchemaNames(new String[] {schema});
        try (Connection connection = database.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        }
        catch (SQLException e) {
            throw new IllegalStateException("Cannot initialize PostGIS schema " + schema, e);
        }
    }

    private static void initialize(PostgreSQLContainer database, String[] schemas) {
        try (Connection connection = database.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            for (String schema : schemas) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Cannot initialize PostGIS schemas "
                + Arrays.toString(schemas), e);
        }
    }

    private static void validateSchemaNames(String[] schemas) {
        for (String schema : schemas) {
            if (schema == null || !schema.matches("[a-z_][a-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid PostgreSQL schema name: " + schema);
            }
        }
    }
}
