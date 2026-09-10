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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.awt.Color;
import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static ch.so.agi.autotest.util.ImageAssertions.assertThatImage;

@Execution(ExecutionMode.SAME_THREAD)
class Json2QgsTests {

    private static final int HTTP_PORT = 80;
    private static final String RESOURCES = "ch/so/agi/autotest/tests/json2qgs/";
    private static final String DUMMY_PROJECT = "/io/data/dummy.qgs";
    private static final String WMS_NAMESPACE = "http://www.opengis.net/wms";
    private static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";
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
        .withCopyFileToContainer(MountableFile.forClasspathResource(RESOURCES + "rasterlayer/single-geotiff.tif", 0644),
            "/io/data/rasterlayer/single-geotiff.tif")
        .withCopyFileToContainer(MountableFile.forClasspathResource(RESOURCES + "rasterlayer/catalogue-left.tif", 0644),
            "/io/data/rasterlayer/catalogue-left.tif")
        .withCopyFileToContainer(MountableFile.forClasspathResource(RESOURCES + "rasterlayer/catalogue-centre.tif", 0644),
            "/io/data/rasterlayer/catalogue-centre.tif")
        .withCopyFileToContainer(MountableFile.forClasspathResource(RESOURCES + "rasterlayer/catalogue-right.tif", 0644),
            "/io/data/rasterlayer/catalogue-right.tif")
        .withCopyFileToContainer(MountableFile.forClasspathResource(RESOURCES + "rasterlayer/image-catalogue.vrt", 0644),
            "/io/data/rasterlayer/image-catalogue.vrt")
        .withExposedPorts(HTTP_PORT)
        .withEnv("QGIS_SERVER_LOG_STDERR", "1")
        .withEnv("QGIS_SERVER_LOG_LEVEL", "0")
        .withEnv("QGIS_SERVER_IGNORE_BAD_LAYERS", "false")
        .withLogConsumer(captureLogs("json2qgs-qgis", QGIS_LOGS))
        .waitingFor(Wait.forHttp(READINESS_PATH)
            .forPort(HTTP_PORT)
            .forStatusCode(200)
            .forResponsePredicate(xml -> publishesWmsLayer(xml, "readiness_point"))
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

    private static boolean publishesWmsLayer(String xml, String layerName) {
        try {
            Element root = parseXml(xml).getDocumentElement();
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
                    && layerName.equals(name.getTextContent().strip())) {
                    return true;
                }
            }
            return false;
        }
        catch (SAXException | IOException e) {
            return false;
        }
    }

    private static Document parseXml(String xml) throws SAXException, IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newNSInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(xml)));
        }
        catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot configure the json2qgs capabilities parser", e);
        }
    }

    private static Element firstElement(Document document, String localName) {
        NodeList elements = document.getElementsByTagNameNS("*", localName);
        assertThat(elements.getLength())
            .as("capabilities should contain a %s element", localName)
            .isPositive();
        return (Element) elements.item(0);
    }

    private static Element directChild(Element parent, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        throw new AssertionError("Expected " + parent.getLocalName() + " to contain " + localName);
    }

    private static String directChildText(Element parent, String localName) {
        return directChild(parent, localName).getTextContent().strip();
    }

    private static Element elementWithChildText(Element parent, String elementName,
        String childName, String expectedChildText) {
        NodeList elements = parent.getElementsByTagNameNS("*", elementName);
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (expectedChildText.equals(directChildText(element, childName))) {
                return element;
            }
        }
        throw new AssertionError("Expected " + parent.getLocalName() + " to contain " + elementName
            + " with " + childName + " " + expectedChildText);
    }

    private static Element elementWithAttribute(Element parent, String elementName,
        String attributeName, String expectedAttributeValue) {
        NodeList elements = parent.getElementsByTagNameNS("*", elementName);
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (expectedAttributeValue.equals(element.getAttribute(attributeName))) {
                return element;
            }
        }
        throw new AssertionError("Expected " + parent.getLocalName() + " to contain " + elementName
            + " with " + attributeName + "=" + expectedAttributeValue);
    }

    private static Response wmsMap(String projectPath, String layerName, String bbox,
        int width, int height) {
        return qgisRequest(projectPath)
            .queryParam("SERVICE", "WMS")
            .queryParam("VERSION", "1.3.0")
            .queryParam("REQUEST", "GetMap")
            .queryParam("LAYERS", layerName)
            .queryParam("CRS", "EPSG:2056")
            .queryParam("BBOX", bbox)
            .queryParam("WIDTH", width)
            .queryParam("HEIGHT", height)
            .queryParam("FORMAT", "image/png")
            .queryParam("TRANSPARENT", "FALSE")
            .when().get();
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
            assertThat(publishesWmsLayer(response.asString(), "readiness_point"))
                .as("GET %s: expected WMS 1.3.0 capabilities publishing readiness_point, received: %s",
                    READINESS_PATH, response.asString())
                .isTrue();
        }
    }

    @Nested
    class Rasterlayer {

        @Test
        void publishesAndRendersSingleGeoTiffRaster() {
            String project = Json2QgsProjects.generateAndPublishWmsProject(JSON2QGS, QGIS_SERVER,
                "rasterlayer/single-geotiff.json", "rasterlayer-single-geotiff");
            assertWmsLayerIsPublished(project, "single_geotiff");

            Response response = wmsMap(project, "single_geotiff",
                "2600000,1200000,2600100,1200090", 100, 90);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatImage(response.asByteArray())
                .isOfType("png")
                .hasSquareColor(45, 40, 10, new Color(214, 93, 44, 255));
        }

        @Test
        void publishesAndRendersImageCatalogueRaster() {
            String project = Json2QgsProjects.generateAndPublishWmsProject(JSON2QGS, QGIS_SERVER,
                "rasterlayer/image-catalogue.json", "rasterlayer-image-catalogue");
            assertWmsLayerIsPublished(project, "image_catalogue");

            Response response = wmsMap(project, "image_catalogue",
                "2600000,1200000,2600300,1200090", 300, 90);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatImage(response.asByteArray())
                .isOfType("png")
                .hasPixelColor(150, 45, new Color(0, 255, 0, 255));
        }

        private void assertWmsLayerIsPublished(String project, String layerName) {
            Response response = qgisRequest(project)
                .queryParam("SERVICE", "WMS")
                .queryParam("VERSION", "1.3.0")
                .queryParam("REQUEST", "GetCapabilities")
                .when().get();

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(publishesWmsLayer(response.asString(), layerName))
                .as("GetCapabilities for %s should publish %s", project, layerName)
                .isTrue();
        }
    }

    @Nested
    class Vectorlayer {

        private static final String FIXTURE = "vectorlayer/rendering.sql";
        private static final String CONFIGURATION = "vectorlayer/rendering.json";
        private static final String PROJECT_NAME = "vectorlayer-rendering";
        private static final String BBOX = "2600000,1200000,2600100,1200100";

        @Test
        void publishesConfiguredVectorLayersInWmsCapabilities() {
            String project = generateVectorProject();

            assertWmsLayerIsPublished(project, "vector_point");
            assertWmsLayerIsPublished(project, "vector_line");
            assertWmsLayerIsPublished(project, "vector_polygon");
            assertWmsLayerIsPublished(project, "vector_multipart_polygon");
        }

        @Test
        void rendersPointVectorLayerInWmsMap() {
            Response response = wmsMap(generateVectorProject(), "vector_point", BBOX, 100, 100);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatImage(response.asByteArray())
                .isOfType("png")
                .hasSquareColor(45, 45, 10, new Color(255, 0, 0, 255));
        }

        @Test
        void rendersLineVectorLayerInWmsMap() {
            Response response = wmsMap(generateVectorProject(), "vector_line", BBOX, 100, 100);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatImage(response.asByteArray())
                .isOfType("png")
                .hasSquareColor(45, 48, 5, new Color(0, 255, 0, 255));
        }

        @Test
        void rendersPolygonAndMultipartPolygonLayersInWmsMap() {
            String project = generateVectorProject();
            Response polygon = wmsMap(project, "vector_polygon", BBOX, 100, 100);
            Response multipartPolygon = wmsMap(project, "vector_multipart_polygon", BBOX, 100, 100);

            assertThat(polygon.statusCode()).isEqualTo(200);
            assertThatImage(polygon.asByteArray())
                .isOfType("png")
                .hasPixelColor(30, 30, new Color(0, 0, 255, 255));
            assertThat(multipartPolygon.statusCode()).isEqualTo(200);
            assertThatImage(multipartPolygon.asByteArray())
                .isOfType("png")
                .hasPixelColor(67, 32, new Color(255, 0, 255, 255))
                .hasPixelColor(67, 72, new Color(255, 0, 255, 255));
        }

        private String generateVectorProject() {
            SqlFixtures.applySql(DATABASE, FIXTURE);
            return Json2QgsProjects.generateAndPublishWmsProject(JSON2QGS, QGIS_SERVER,
                CONFIGURATION, PROJECT_NAME);
        }

        private void assertWmsLayerIsPublished(String project, String layerName) {
            Response response = qgisRequest(project)
                .queryParam("SERVICE", "WMS")
                .queryParam("VERSION", "1.3.0")
                .queryParam("REQUEST", "GetCapabilities")
                .when().get();

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(publishesWmsLayer(response.asString(), layerName))
                .as("GetCapabilities for %s should publish %s", project, layerName)
                .isTrue();
        }
    }

    @Nested
    class MetadataMapped {

        private static final String WMS_CONFIGURATION = "metadata/mapped-wms.json";
        private static final String WMS_PROJECT = "metadata-mapped-wms";
        private static final String WFS_CONFIGURATION = "metadata/mapped-wfs.json";
        private static final String WFS_PROJECT = "metadata-mapped-wfs";

        @Test
        void returnsConfiguredWmsServiceMetadataInCapabilities() throws SAXException, IOException {
            Document capabilities = wmsCapabilities(generateWmsProject());
            Element service = directChild(capabilities.getDocumentElement(), "Service");

            assertThat(directChildText(service, "Name")).isEqualTo("WMS");
            assertThat(directChildText(service, "Title")).isEqualTo("Mapped WMS service");
            assertThat(directChildText(service, "Abstract"))
                .isEqualTo("WMS metadata supplied by the fixture");
            Element keywordList = directChild(service, "KeywordList");
            assertThat(keywordList.getTextContent()).contains("metadata", "wms-mapped");
            assertThat(directChild(service, "OnlineResource").getAttributeNS(XLINK_NAMESPACE, "href"))
                .isEqualTo("https://example.test/services/mapped-wms");
            Element contact = directChild(service, "ContactInformation");
            assertThat(directChildText(directChild(contact, "ContactPersonPrimary"), "ContactPerson"))
                .isEqualTo("Metadata Maintainer");
            assertThat(directChildText(directChild(contact, "ContactPersonPrimary"), "ContactOrganization"))
                .isEqualTo("Autotest Mapping Office");
            assertThat(directChildText(contact, "ContactPosition")).isEqualTo("Service owner");
            assertThat(directChildText(contact, "ContactVoiceTelephone")).isEqualTo("+41-32-000-00-00");
            assertThat(directChildText(contact, "ContactElectronicMailAddress"))
                .isEqualTo("metadata@example.test");
            assertThat(directChildText(service, "Fees")).isEqualTo("none");
            assertThat(directChildText(service, "AccessConstraints")).isEqualTo("public");

            Element rootLayer = directChild(directChild(capabilities.getDocumentElement(), "Capability"), "Layer");
            assertThat(directChildText(rootLayer, "Name")).isEqualTo("mapped_wms_root");
            assertThat(rootLayer.getTextContent()).contains("EPSG:2056", "EPSG:4326");
            assertThat(elementWithAttribute(rootLayer, "BoundingBox", "CRS", "EPSG:2056")
                .getAttribute("minx")).isEqualTo("2599900");
            assertThat(elementWithAttribute(rootLayer, "BoundingBox", "CRS", "EPSG:2056")
                .getAttribute("maxy")).isEqualTo("1200100");
        }

        @Test
        void returnsConfiguredWmsLayerMetadataInCapabilities() throws SAXException, IOException {
            Document capabilities = wmsCapabilities(generateWmsProject());
            Element rootLayer = directChild(directChild(capabilities.getDocumentElement(), "Capability"), "Layer");
            Element layer = elementWithChildText(rootLayer, "Layer", "Name", "mapped_wms_layer");

            assertThat(directChildText(layer, "Title")).isEqualTo("Mapped WMS layer");
        }

        @Test
        void returnsConfiguredWfsServiceMetadataInCapabilities() throws SAXException, IOException {
            Document capabilities = wfsCapabilities(generateWfsProject());
            Element service = firstElement(capabilities, "ServiceIdentification");

            assertThat(directChildText(service, "Title")).isEqualTo("Mapped WFS service");
            assertThat(directChildText(service, "Abstract"))
                .isEqualTo("WFS metadata supplied by the fixture");
            assertThat(directChild(service, "Keywords").getTextContent()).contains("metadata", "wfs-mapped");
            assertThat(directChildText(service, "Fees")).isEqualTo("none");
            assertThat(directChildText(service, "AccessConstraints")).isEqualTo("public");
            assertThat(directChildText(service, "ServiceType")).isEqualTo("WFS");
            Element getCapabilities = elementWithAttribute(firstElement(capabilities, "OperationsMetadata"),
                "Operation", "name", "GetCapabilities");
            Element http = directChild(directChild(getCapabilities, "DCP"), "HTTP");
            assertThat(directChild(http, "Get").getAttributeNS(XLINK_NAMESPACE, "href"))
                .isEqualTo("https://example.test/services/mapped-wfs");
        }

        @Test
        void returnsConfiguredWfsFeatureClassMetadataInCapabilities() throws SAXException, IOException {
            Document capabilities = wfsCapabilities(generateWfsProject());
            Element featureClass = elementWithChildText(firstElement(capabilities, "FeatureTypeList"),
                "FeatureType", "Name", "mapped_wfs_featureclass");

            assertThat(directChildText(featureClass, "Title")).isEqualTo("Mapped WFS feature class");
        }

        private String generateWmsProject() {
            return Json2QgsProjects.generateAndPublishWmsProject(JSON2QGS, QGIS_SERVER,
                WMS_CONFIGURATION, WMS_PROJECT);
        }

        private String generateWfsProject() {
            return Json2QgsProjects.generateAndPublishWfsProject(JSON2QGS, QGIS_SERVER,
                WFS_CONFIGURATION, WFS_PROJECT);
        }

        private Document wmsCapabilities(String project) throws SAXException, IOException {
            Response response = qgisRequest(project)
                .queryParam("SERVICE", "WMS")
                .queryParam("VERSION", "1.3.0")
                .queryParam("REQUEST", "GetCapabilities")
                .when().get();
            assertThat(response.statusCode()).isEqualTo(200);
            return parseXml(response.asString());
        }

        private Document wfsCapabilities(String project) throws SAXException, IOException {
            Response response = qgisRequest(project)
                .queryParam("SERVICE", "WFS")
                .queryParam("VERSION", "1.1.0")
                .queryParam("REQUEST", "GetCapabilities")
                .when().get();
            assertThat(response.statusCode()).isEqualTo(200);
            return parseXml(response.asString());
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
