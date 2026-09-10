package ch.so.agi.autotest.tests.json2qgs;

import ch.so.agi.autotest.util.SqlFixtures;
import ch.so.agi.autotest.util.HttpTraffic;
import ch.so.agi.autotest.util.PostgisContainers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@Execution(ExecutionMode.SAME_THREAD)
class Json2QgsTests {

    private static final int HTTP_PORT = 80;
    private static final String RESOURCES = "ch/so/agi/autotest/tests/json2qgs/";
    private static final String DUMMY_PROJECT = "/io/data/dummy.qgs";
    private static final String WMS_NAMESPACE = "http://www.opengis.net/wms";
    private static final String READINESS_PATH = "/ows/?MAP=" + DUMMY_PROJECT
        + "&SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities";
    private static final StringBuffer DATABASE_LOGS = new StringBuffer();
    private static final StringBuffer QGIS_LOGS = new StringBuffer();
    private static final Network NETWORK = Network.newNetwork();
    private static PostgreSQLContainer DATABASE;
    private static final GenericContainer<?> QGIS_SERVER = new GenericContainer<>(
        DockerImageName.parse("qgis/qgis-server:3.44.14"))
        .withNetwork(NETWORK)
        .withCopyFileToContainer(MountableFile.forClasspathResource(RESOURCES + "readiness/dummy.qgs", 0644),
            DUMMY_PROJECT)
        .withExposedPorts(HTTP_PORT)
        .withEnv("QGIS_SERVER_LOG_STDERR", "1")
        .withEnv("QGIS_SERVER_LOG_LEVEL", "0")
        .withEnv("QGIS_SERVER_IGNORE_BAD_LAYERS", "false")
        .withLogConsumer(captureLogs("json2qgs-qgis", QGIS_LOGS))
        .waitingFor(Wait.forHttp(READINESS_PATH)
            .forPort(HTTP_PORT)
            .forStatusCode(200)
            .forResponsePredicate(Json2QgsTests::publishesReadinessLayer)
            .withReadTimeout(Duration.ofSeconds(10))
            .withStartupTimeout(Duration.ofSeconds(120)));
    private static final GenericContainer<?> JSON2QGS = new GenericContainer<>(
        DockerImageName.parse("sogis/json2qgs:v1.0.9"))
        .withCommand("sh", "-c", "python -m http.server 8080 --directory /srv/json2qgs/schemas "
            + "& tail -f /dev/null");

    @BeforeAll
    static void startEnvironment() {
        DATABASE = PostgisContainers.provision(NETWORK,
            captureLogs("json2qgs-postgres", DATABASE_LOGS));
        PostgisContainers.ensureSchema(DATABASE, Readiness.SCHEMA);
        SqlFixtures.applySql(DATABASE, "readiness/init.sql");
        PostgisContainers.ensureSchema(DATABASE, Featureclass.SCHEMA);
        startQgisServer();
        JSON2QGS.start();
    }

    private static void startQgisServer() {
        try {
            QGIS_SERVER.start();
        } catch (Exception | AssertionError failure) {
            failure.addSuppressed(new IllegalStateException(
                "json2qgs environment startup failed; expected WMS 1.3.0 capabilities with layer "
                    + "readiness_point at " + READINESS_PATH
                    + "\nPostGIS logs:\n" + DATABASE_LOGS + "\nQGIS logs:\n" + QGIS_LOGS));
            throw failure;
        }
    }

    @AfterAll
    static void stopEnvironment() {
        try {
            JSON2QGS.stop();
        }
        finally {
            try {
                QGIS_SERVER.stop();
            }
            finally {
                try {
                    DATABASE.stop();
                }
                finally {
                    NETWORK.close();
                }
            }
        }
    }

    private static Consumer<OutputFrame> captureLogs(String name, StringBuffer logs) {
        Consumer<OutputFrame> trafficLog = HttpTraffic.containerLogConsumer(name);
        return frame -> {
            if (frame.getUtf8String() != null) {
                logs.append(frame.getUtf8String());
            }
            trafficLog.accept(frame);
        };
    }

    private static RequestSpecification qgisRequest(String projectPath) {
        return QgisServerRequests.forProject(QGIS_SERVER, HTTP_PORT, projectPath);
    }

    private static RequestSpecification oapifRequest(String projectPath, String resourcePath) {
        return QgisServerRequests.forOapifProject(QGIS_SERVER, HTTP_PORT, projectPath, resourcePath);
    }

    private static boolean publishesReadinessLayer(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newNSInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            if (!WMS_NAMESPACE.equals(root.getNamespaceURI())
                || !"WMS_Capabilities".equals(root.getLocalName())
                || !"1.3.0".equals(root.getAttribute("version"))) {
                return false;
            }
            NodeList names = root.getElementsByTagNameNS(WMS_NAMESPACE, "Name");
            for (int i = 0; i < names.getLength(); i++) {
                var name = names.item(i);
                var parent = name.getParentNode();
                if ("Layer".equals(parent.getLocalName())
                    && WMS_NAMESPACE.equals(parent.getNamespaceURI())
                    && "readiness_point".equals(name.getTextContent().strip())) {
                    return true;
                }
            }
            return false;
        }
        catch (SAXException | IOException e) {
            return false;
        }
        catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot configure the json2qgs capabilities parser", e);
        }
    }

    @Nested
    class Readiness {

        private static final String SCHEMA = "json2qgs";

        @Test
        void publishesDummyPostgisLayerInWmsCapabilities() {
            Response response = qgisRequest(DUMMY_PROJECT)
                .queryParam("SERVICE", "WMS")
                .queryParam("VERSION", "1.3.0")
                .queryParam("REQUEST", "GetCapabilities")
                .when()
                .get();

            assertThat(response.statusCode())
                .as("GET %s: dummy PostGIS project response: %s", READINESS_PATH, response.asString())
                .isEqualTo(200);
            assertThat(publishesReadinessLayer(response.asString()))
                .as("GET %s: expected WMS 1.3.0 capabilities publishing readiness_point, received: %s",
                    READINESS_PATH, response.asString())
                .isTrue();
        }
    }

    @Nested
    class Featureclass {

        private static final String SCHEMA = "json2qgs";

        @Test
        void returnsOnlyConfiguredAttributes() {
            SqlFixtures.applySql(DATABASE, "featureclass/attribute-inclusion.sql");
            String project = Json2QgsProjects.generateAndPublishWfsProject(JSON2QGS, QGIS_SERVER,
                "featureclass/attribute-inclusion.json", "featureclass-attribute-inclusion");
            assertOapifCollectionIsPublished(project, "attribute_inclusion");

            Response response = oapifFeatures(project, "attribute_inclusion");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.asString()).node("features[0].properties").isEqualTo("""
                {"id":1,"included_text":"visible"}
                """);
        }

        @Test
        void serializesNonspatialAttributeTypesInGeoJson() {
            SqlFixtures.applySql(DATABASE, "featureclass/nonspatial-types.sql");
            String project = Json2QgsProjects.generateAndPublishWfsProject(JSON2QGS, QGIS_SERVER,
                "featureclass/nonspatial-types.json", "featureclass-nonspatial-types");
            assertOapifCollectionIsPublished(project, "nonspatial_types");

            Response response = oapifFeatures(project, "nonspatial_types");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.asString()).node("features[0].properties").isEqualTo("""
                {"id":1,"text_value":"text","integer_value":42,"decimal_value":12.34,"boolean_value":true,"uuid_value":"123e4567-e89b-12d3-a456-426614174000","json_value":{"kind":"fixture"},"date_value":"2024-01-02","time_value":"03:04:05.000","timestamp_value":"2024-01-02T03:04:05.000","nullable_value":null}
                """);
        }

        @Test
        void serializesSpatialAttributesIncludingOptionalAndMultipartGeometries() {
            SqlFixtures.applySql(DATABASE, "featureclass/spatial-types.sql");
            String project = Json2QgsProjects.generateAndPublishWfsProject(JSON2QGS, QGIS_SERVER,
                "featureclass/spatial-types.json", "featureclass-spatial-types");
            assertOapifCollectionIsPublished(project, "two_geometries_point");
            assertOapifCollectionIsPublished(project, "two_geometries_line");
            assertOapifCollectionIsPublished(project, "optional_point");
            assertOapifCollectionIsPublished(project, "multipart_polygon");

            Response response = oapifFeatures(project, "multipart_polygon");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.asString()).node("features[0].geometry.type").isEqualTo("MultiPolygon");

            assertThat(oapifFeatures(project, "two_geometries_point")
                .jsonPath().getString("features[0].geometry.type")).isEqualTo("Point");
            assertThat(oapifFeatures(project, "two_geometries_line")
                .jsonPath().getString("features[0].geometry.type")).isEqualTo("LineString");
            Object optionalGeometry = oapifFeatures(project, "optional_point")
                .jsonPath().get("features[0].geometry");
            assertThat(optionalGeometry).isNull();
        }

        private Response oapifFeatures(String project, String collectionId) {
            return oapifRequest(project, "/collections/" + collectionId + "/items")
                .accept("application/geo+json")
                .when().get()
                .then().statusCode(200)
                .extract().response();
        }

        private void assertOapifCollectionIsPublished(String project, String collectionId) {
            Response collections = oapifRequest(project, "/collections")
                .when().get();
            assertThat(collections.statusCode()).isEqualTo(200);
            assertThat(collections.jsonPath().getList("collections.id", String.class))
                .contains(collectionId);

            Response collection = oapifRequest(project, "/collections/" + collectionId)
                .when().get();
            assertThat(collection.statusCode()).isEqualTo(200);
            assertThat(collection.jsonPath().getString("id")).isEqualTo(collectionId);
        }
    }
}
