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
import org.junit.jupiter.api.TestInstance;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;

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
    @BeforeAll
    static void startEnvironment() throws SQLException {
        DATABASE.start();
        try (Connection connection = DATABASE.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA dataservice");
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        }
    }

    @AfterAll
    static void stopEnvironment() {
        DATABASE.stop();
        NETWORK.close();
    }

    private static GenericContainer<?> startDataService(String configurationDirectory) {
        String resourceDirectory = "ch/so/agi/autotest/tests/dataservice/" + configurationDirectory;
        GenericContainer<?> dataService = new GenericContainer<>(
            DockerImageName.parse("sourcepole/qwc-data-service:v2026.1-lts"))
            .withNetwork(NETWORK)
            .withCopyFileToContainer(MountableFile.forClasspathResource(
                resourceDirectory + "/dataConfig.json"), "/config/default/dataConfig.json")
            .withCopyFileToContainer(MountableFile.forClasspathResource(
                resourceDirectory + "/permissions.json"), "/config/default/permissions.json")
            .withEnv("CONFIG_PATH", "/config")
            .withExposedPorts(HTTP_PORT)
            .withLogConsumer(HttpTraffic.containerLogConsumer("data-service"))
            .waitingFor(Wait.forHttp("/ready").forStatusCode(200));
        dataService.start();
        return dataService;
    }

    private static RequestSpecification dataServiceRequest(GenericContainer<?> dataService) {
        return given()
            .filters(HttpTraffic.restAssuredFilters())
            .baseUri("http://%s:%d".formatted(
                dataService.getHost(), dataService.getMappedPort(HTTP_PORT)));
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class NonSpatialTypesTest {

        private GenericContainer<?> dataService;

        @BeforeAll
        void startDataService() {
            dataService = DataServiceTests.startDataService("non-spatial-types");
        }

        @AfterAll
        void stopDataService() {
            dataService.stop();
        }

        @Test
        void anonymousPublicReadSerializesNonSpatialColumnTypesAsGeoJson() {
            SqlFixtures.applySql(DATABASE, "nonspatial-types.sql");
            Response response = dataServiceRequest(dataService)
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class SpatialResponses {

        private GenericContainer<?> dataService;

        @BeforeAll
        void startDataService() {
            dataService = DataServiceTests.startDataService("spatial-responses");
        }

        @AfterAll
        void stopDataService() {
            dataService.stop();
        }

        @BeforeEach
        void loadSpatialResponseFixture() {
            SqlFixtures.applySql(DATABASE, "spatial-responses.sql");
        }

        @Test
        void configuredSpatialFieldIsSerializedAsGeoJsonWithEpsg2056Coordinates() {
            Response response = dataServiceRequest(dataService)
                .queryParam("filter", "[\"id\",\"=\",1]")
                .when()
                .get("/api/v1/data/dataservice.spatial_responses/")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

            assertThatJson(response.asString()).when(IGNORING_EXTRA_FIELDS).isEqualTo("""
                {
                  "type": "FeatureCollection",
                  "numberMatched": 1,
                  "numberReturned": 1,
                  "features": [
                    {
                      "type": "Feature",
                      "id": 1,
                      "geometry": {
                        "type": "Point",
                        "coordinates": [2600000, 1200000]
                      },
                      "properties": {"id": 1, "label": "Configured point"}
                    }
                  ]
                }
                """);
        }

        @Test
        void nullConfiguredSpatialFieldIsSerializedAsNullGeometry() {
            Response response = dataServiceRequest(dataService)
                .queryParam("filter", "[\"id\",\"=\",2]")
                .when()
                .get("/api/v1/data/dataservice.spatial_responses/")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .response();

            assertThatJson(response.asString()).when(IGNORING_EXTRA_FIELDS).isEqualTo("""
                {
                  "type": "FeatureCollection",
                  "numberMatched": 1,
                  "numberReturned": 1,
                  "features": [
                    {
                      "type": "Feature",
                      "id": 2,
                      "geometry": null,
                      "properties": {"id": 2, "label": "No geometry"}
                    }
                  ]
                }
                """);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ModifyFeature {

        private static final String DATASET_PATH = "/api/v1/data/dataservice.modify_features/";
        private GenericContainer<?> dataService;

        @BeforeAll
        void startDataService() {
            dataService = DataServiceTests.startDataService("modify-feature");
        }

        @AfterAll
        void stopDataService() {
            dataService.stop();
        }

        @BeforeEach
        void loadModifyFeatureFixture() {
            SqlFixtures.applySql(DATABASE, "modify-features.sql");
        }

        @Test
        void permittedClientCreatesFeatureWithSpatialAndNonSpatialAttributes() {
            Response response = dataServiceRequest(dataService)
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

            assertThatJson(dataServiceRequest(dataService).when().get(DATASET_PATH + featureId).then()
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
            dataServiceRequest(dataService)
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

            assertThatJson(dataServiceRequest(dataService).when().get(DATASET_PATH + "1").then()
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
            dataServiceRequest(dataService)
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

            assertThatJson(dataServiceRequest(dataService).when().get(DATASET_PATH + "1").then()
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
            dataServiceRequest(dataService)
                    .when()
                    .delete(DATASET_PATH + "1")
                    .then()
                    .statusCode(200)
                    .body("message", equalTo("Dataset feature deleted"));

            dataServiceRequest(dataService)
                    .when()
                    .get(DATASET_PATH + "1")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class FilterTest {

        private GenericContainer<?> dataService;

        @BeforeAll
        void startDataService() {
            dataService = DataServiceTests.startDataService("filter");
        }

        @AfterAll
        void stopDataService() {
            dataService.stop();
        }

        @Test
        void filterByAttribute() {
            SqlFixtures.applySql(DATABASE, "filter-types.sql");

            dataServiceRequest(dataService)
                    .queryParam("filter", "[\"category\",\"=\",\"selected\"]")
                    .when()
                    .get("/api/v1/data/dataservice.filter_types/")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("FeatureCollection"))
                    .body("numberMatched", equalTo(2))
                    .body("numberReturned", equalTo(2))
                    .body("features.id", contains(1, 3));
        }

        @Test
        void filterByBoundingBox() {
            SqlFixtures.applySql(DATABASE, "filter-types.sql");

            dataServiceRequest(dataService)
                    .queryParam("bbox", "2599000,1199000,2601000,1201000")
                    .when()
                    .get("/api/v1/data/dataservice.filter_types/")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("FeatureCollection"))
                    .body("numberMatched", equalTo(2))
                    .body("numberReturned", equalTo(2))
                    .body("features.id", contains(1, 2));
        }

        @Test
        void filterByGeometry() {
            SqlFixtures.applySql(DATABASE, "filter-types.sql");

            dataServiceRequest(dataService)
                    .queryParam("filter_geom", """
                {"type":"Polygon","crs":{"type":"name","properties":{"name":"EPSG:2056"}},"coordinates":[[[2599000,1199000],
                [2601000,1199000],[2601000,1201000],[2599000,1201000],
                [2599000,1199000]]]}
                """)
                    .when()
                    .get("/api/v1/data/dataservice.filter_types/")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("FeatureCollection"))
                    .body("numberMatched", equalTo(2))
                    .body("numberReturned", equalTo(2))
                    .body("features.id", contains(1, 2));
        }

        @Test
        void combinedAttributeAndBoundingBoxFilters() {
            SqlFixtures.applySql(DATABASE, "filter-types.sql");

            dataServiceRequest(dataService)
                    .queryParam("filter", "[\"category\",\"=\",\"selected\"]")
                    .queryParam("bbox", "2599000,1199000,2601000,1201000")
                    .when()
                    .get("/api/v1/data/dataservice.filter_types/")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("FeatureCollection"))
                    .body("numberMatched", equalTo(1))
                    .body("numberReturned", equalTo(1))
                    .body("features.id", contains(1));
        }

        @Test
        void combinedAttributeAndGeometryFilters() {
            SqlFixtures.applySql(DATABASE, "filter-types.sql");

            dataServiceRequest(dataService)
                    .queryParam("filter", "[\"category\",\"=\",\"selected\"]")
                    .queryParam("filter_geom", """
                {"type":"Polygon","crs":{"type":"name","properties":{"name":"EPSG:2056"}},"coordinates":[[[2599000,1199000],
                [2601000,1199000],[2601000,1201000],[2599000,1201000],
                [2599000,1199000]]]}
                """)
                    .when()
                    .get("/api/v1/data/dataservice.filter_types/")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("FeatureCollection"))
                    .body("numberMatched", equalTo(1))
                    .body("numberReturned", equalTo(1))
                    .body("features.id", contains(1));
        }
    }
}
