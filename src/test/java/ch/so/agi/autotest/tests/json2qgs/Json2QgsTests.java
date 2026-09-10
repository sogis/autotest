package ch.so.agi.autotest.tests.json2qgs;

import ch.so.agi.autotest.util.HttpTraffic;
import ch.so.agi.autotest.util.SqlFixtures;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.5-alpine")
            .asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("autotest")
        .withUsername("autotest")
        .withPassword("autotest")
        .withNetwork(NETWORK)
        .withNetworkAliases("postgres")
        .withLogConsumer(captureLogs("json2qgs-postgres", DATABASE_LOGS))
        .waitingFor(Wait.forSuccessfulCommand("pg_isready -h 127.0.0.1 -U autotest -d autotest")
            .withStartupTimeout(Duration.ofSeconds(120)));
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

    @BeforeAll
    static void startEnvironment() {
        DATABASE.start();
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
        return given()
            .filters(HttpTraffic.restAssuredFilters())
            .baseUri("http://%s:%d".formatted(
                QGIS_SERVER.getHost(), QGIS_SERVER.getMappedPort(HTTP_PORT)))
            .basePath("/ows/")
            .queryParam("MAP", projectPath);
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Readiness {

        @BeforeAll
        void prepareReadinessScenario() {
            SqlFixtures.applySql(DATABASE, "readiness/init.sql");
            startQgisServer();
        }

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
}
