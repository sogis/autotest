package ch.so.agi.autotest.tests.json2qgs;

import ch.so.agi.autotest.util.HttpTraffic;
import io.restassured.specification.RequestSpecification;
import org.testcontainers.containers.GenericContainer;

import static io.restassured.RestAssured.given;

final class QgisServerRequests {

    private QgisServerRequests() {
    }

    static RequestSpecification forProject(GenericContainer<?> qgisServer, int httpPort,
        String projectPath) {
        return forProject(qgisServer, httpPort, projectPath, "/ows/");
    }

    static RequestSpecification forOapifProject(GenericContainer<?> qgisServer, int httpPort,
        String projectPath, String resourcePath) {
        return forProject(qgisServer, httpPort, projectPath, "/wfs3" + resourcePath);
    }

    private static RequestSpecification forProject(GenericContainer<?> qgisServer, int httpPort,
        String projectPath, String basePath) {
        return given()
            .filters(HttpTraffic.restAssuredFilters())
            .baseUri("http://%s:%d".formatted(
                qgisServer.getHost(), qgisServer.getMappedPort(httpPort)))
            .basePath(basePath)
            .queryParam("MAP", projectPath);
    }
}
