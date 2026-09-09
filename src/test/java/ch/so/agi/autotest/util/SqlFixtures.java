package ch.so.agi.autotest.util;

import org.testcontainers.containers.JdbcDatabaseContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqlFixtures {

    private SqlFixtures() {
    }

    public static void applySql(JdbcDatabaseContainer<?> database, String fixtureName) {
        Class<?> testClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
            .getCallerClass();

        try (Connection connection = database.createConnection("");
             InputStream stream = testClass.getResourceAsStream(fixtureName)) {
            if (stream == null) {
                throw new IOException("Missing SQL fixture '%s' for %s".formatted(
                    fixtureName, testClass.getName()));
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
