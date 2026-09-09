package ch.so.agi.autotest.tests.dataservice;

import ch.so.agi.autotest.util.HttpTraffic;
import ch.so.agi.autotest.util.SqlFixtures;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static org.hamcrest.Matchers.equalTo;

@Execution(ExecutionMode.SAME_THREAD)
class DataServiceTests {

    private static final int HTTP_PORT = 9090;
    private static final Network NETWORK = Network.newNetwork();
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.5-alpine")
            .asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("autotest")
        .withUsername("autotest")
        .withPassword("autotest")
        .withNetwork(NETWORK)
        .withNetworkAliases("postgres");
    private static final GenericContainer<?> DATA_SERVICE = new GenericContainer<>(
        DockerImageName.parse("sourcepole/qwc-data-service:v2026.1-lts"))
        .withNetwork(NETWORK)
        .withCopyFileToContainer(MountableFile.forClasspathResource(
            "ch/so/agi/autotest/tests/dataservice/default/dataConfig.json"),
            "/config/default/dataConfig.json")
        .withCopyFileToContainer(MountableFile.forClasspathResource(
            "ch/so/agi/autotest/tests/dataservice/default/permissions.json"),
            "/config/default/permissions.json")
        .withEnv("CONFIG_PATH", "/config")
        .withExposedPorts(HTTP_PORT)
        .withLogConsumer(HttpTraffic.containerLogConsumer("data-service"))
        .waitingFor(Wait.forHttp("/ready").forStatusCode(200));

    @BeforeAll
    static void startEnvironment() throws SQLException {
        DATABASE.start();
        try (Connection connection = DATABASE.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA dataservice");
        }
        DATA_SERVICE.start();
    }

    @AfterAll
    static void stopEnvironment() {
        DATA_SERVICE.stop();
        DATABASE.stop();
        NETWORK.close();
    }

    private static RequestSpecification dataServiceRequest() {
        return given()
            .filters(HttpTraffic.restAssuredFilters())
            .baseUri("http://%s:%d".formatted(
                DATA_SERVICE.getHost(), DATA_SERVICE.getMappedPort(HTTP_PORT)));
    }

    @Nested
    class NonSpatialTypesTest {

        @Test
        void anonymousPublicReadSerializesNonSpatialColumnTypesAsGeoJson() {
            SqlFixtures.applySql(DATABASE, "nonspatial-types.sql");
            Response response = dataServiceRequest()
            .when()
                .get("/api/v1/data/dataservice.attribute_types/")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

            assertThatJson(response.asString()).isEqualTo("""
                {
                  "type": "FeatureCollection",
                  "numberMatched": 1,
                  "numberReturned": 1,
                  "features": [
                    {
                      "type": "Feature",
                      "id": 1,
                      "geometry": null,
                      "properties": {
                        "id": 1,
                        "integer_value": 42,
                        "bigint_value": 2147483648,
                        "decimal_value": 12.34,
                        "boolean_value": true,
                        "uuid_value": "123e4567-e89b-12d3-a456-426614174000",
                        "date_value": "2025-02-03",
                        "datetime_value": "2025-02-03T04:05:06",
                        "varchar_value": "Ada",
                        "text_value": "Public datatype fixture",
                        "json_value": {"category": "example", "rank": 2},
                        "jsonb_value": {"category": "example", "rank": 2},
                        "nullable_text_value": null
                      }
                    }
                  ]
                }
                """);
        }
    }

    @Nested
    class ModifyFeature {

        private static final String DATASET_PATH = "/api/v1/data/dataservice.modify_features/";

        @BeforeEach
        void loadModifyFeatureFixture() {
            SqlFixtures.applySql(DATABASE, "modify-features.sql");
        }

        @Test
        void permittedClientCreatesFeatureWithSpatialAndNonSpatialAttributes() {
            Response response = dataServiceRequest()
                .contentType(ContentType.JSON)
                .body("""
                    {
                      "type": "Feature",
                      "crs": {"type": "name", "properties": {"name": "urn:ogc:def:crs:EPSG::2056"}},
                      "geometry": {"type": "Point", "coordinates": [2600050, 1200060]},
                      "properties": {"name": "Created feature", "category": "created"}
                    }
                    """)
            .when()
                .post(DATASET_PATH)
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .extract()
                .response();

            int featureId = response.path("id");

            assertThatJson(dataServiceRequest().when().get(DATASET_PATH + featureId).then()
                .statusCode(200)
                .extract().asString()).when(IGNORING_EXTRA_FIELDS).isEqualTo("""
                {
                  "type": "Feature",
                  "id": %d,
                  "geometry": {"type": "Point", "coordinates": [2600050, 1200060]},
                  "properties": {"id": %d, "name": "Created feature", "category": "created"}
                }
                """.formatted(featureId, featureId));
        }

        @Test
        void permittedClientUpdatesExistingFeatureGeometry() {
            dataServiceRequest()
                .contentType(ContentType.JSON)
                .body("""
                    {
                      "type": "Feature",
                      "id": 1,
                      "crs": {"type": "name", "properties": {"name": "urn:ogc:def:crs:EPSG::2056"}},
                      "geometry": {"type": "Point", "coordinates": [2600100, 1200100]},
                      "properties": {"id": 1, "name": "Existing feature", "category": "baseline"}
                    }
                    """)
            .when()
                .put(DATASET_PATH + "1")
            .then()
                .statusCode(200);

            assertThatJson(dataServiceRequest().when().get(DATASET_PATH + "1").then()
                .statusCode(200)
                .extract().asString()).when(IGNORING_EXTRA_FIELDS).isEqualTo("""
                {
                  "type": "Feature",
                  "id": 1,
                  "geometry": {"type": "Point", "coordinates": [2600100, 1200100]},
                  "properties": {"id": 1, "name": "Existing feature", "category": "baseline"}
                }
                """);
        }

        @Test
        void permittedClientUpdatesExistingFeatureNonSpatialAttributes() {
            dataServiceRequest()
                .contentType(ContentType.JSON)
                .body("""
                    {
                      "type": "Feature",
                      "id": 1,
                      "crs": {"type": "name", "properties": {"name": "urn:ogc:def:crs:EPSG::2056"}},
                      "geometry": {"type": "Point", "coordinates": [2600000, 1200000]},
                      "properties": {"id": 1, "name": "Renamed feature", "category": "updated"}
                    }
                    """)
            .when()
                .put(DATASET_PATH + "1")
            .then()
                .statusCode(200);

            assertThatJson(dataServiceRequest().when().get(DATASET_PATH + "1").then()
                .statusCode(200)
                .extract().asString()).when(IGNORING_EXTRA_FIELDS).isEqualTo("""
                {
                  "type": "Feature",
                  "id": 1,
                  "geometry": {"type": "Point", "coordinates": [2600000, 1200000]},
                  "properties": {"id": 1, "name": "Renamed feature", "category": "updated"}
                }
                """);
        }

        @Test
        void permittedClientDeletesExistingFeature() {
            dataServiceRequest()
            .when()
                .delete(DATASET_PATH + "1")
            .then()
                .statusCode(200)
                .body("message", equalTo("Dataset feature deleted"));

            dataServiceRequest()
            .when()
                .get(DATASET_PATH + "1")
            .then()
                .statusCode(404);
        }
    }
}
