package ch.so.agi.autotest.httpbin;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@Testcontainers
class HttpbinTest {

    private static final int HTTP_PORT = 8080;

    @Container
    private static final GenericContainer<?> HTTPBIN = new GenericContainer<>(
        DockerImageName.parse("ghcr.io/mccutchen/go-httpbin:2.25.0"))
        .withExposedPorts(HTTP_PORT)
        .waitingFor(Wait.forHttp("/get").forStatusCode(200));

    @Test
    void getEchoesQueryParameterAndHeader() {
        Response response = given()
            .baseUri(baseUrl())
            .queryParam("name", "Ada")
            .header("X-Test-Case", "get-echo")
        .when()
            .get("/get")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        assertThatJson(response.asString())
            .when(IGNORING_EXTRA_FIELDS)
            .isEqualTo("""
                {
                  "args": {"name": ["Ada"]},
                  "headers": {"X-Test-Case": ["get-echo"]}
                }
                """);
    }

    @Test
    void postEchoesJsonBody() {
        Response response = given()
            .baseUri(baseUrl())
            .contentType(ContentType.JSON)
            .body("""
                {
                  "message": "hello",
                  "count": 2
                }
                """)
        .when()
            .post("/post")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        assertThatJson(response.asString())
            .when(IGNORING_EXTRA_FIELDS)
            .isEqualTo("""
                {
                  "json": {
                    "message": "hello",
                    "count": 2
                  }
                }
                """);
    }

    private static String baseUrl() {
        return "http://%s:%d".formatted(HTTPBIN.getHost(), HTTPBIN.getMappedPort(HTTP_PORT));
    }
}
