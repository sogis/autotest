package ch.so.agi.autotest.tests.json2qgs;

import org.assertj.core.api.Assertions;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Json2QgsProjects {

    private static final String RESOURCE_ROOT = "ch/so/agi/autotest/tests/json2qgs/";

    private Json2QgsProjects() {
    }

    static String generateAndPublishWfsProject(GenericContainer<?> generator,
        GenericContainer<?> qgisServer, String configurationResource, String projectName) {
        return generateAndPublishProject(generator, qgisServer, configurationResource, projectName, "wfs");
    }

    static String generateAndPublishWmsProject(GenericContainer<?> generator,
        GenericContainer<?> qgisServer, String configurationResource, String projectName) {
        return generateAndPublishProject(generator, qgisServer, configurationResource, projectName, "wms");
    }

    private static String generateAndPublishProject(GenericContainer<?> generator,
        GenericContainer<?> qgisServer, String configurationResource, String projectName, String mode) {
        String configurationPath = "/tmp/" + projectName + ".json";
        String generatedProject = "/tmp/" + projectName + ".qgs";
        generator.copyFileToContainer(
            MountableFile.forClasspathResource(RESOURCE_ROOT + configurationResource),
            configurationPath);
        Container.ExecResult result;
        try {
            result = generator.execInContainer("python", "/srv/json2qgs/json2qgs.py",
                configurationPath, mode, "/tmp", "3", "--qgsTemplateDir", "/srv/json2qgs/qgs/",
                "--qgsName", projectName);
        }
        catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Cannot generate QGIS project " + projectName, e);
        }
        Assertions.assertThat(result.getExitCode())
            .as("json2qgs %s project %s: stdout=%s stderr=%s", mode, projectName,
                result.getStdout(), result.getStderr())
            .isZero();

        try {
            Path stagingFile = Files.createTempFile(projectName, ".qgs");
            try {
                generator.copyFileFromContainer(generatedProject,
                    stream -> Files.copy(stream, stagingFile,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING));
                qgisServer.copyFileToContainer(MountableFile.forHostPath(stagingFile),
                    "/io/data/" + projectName + ".qgs");
            }
            finally {
                Files.deleteIfExists(stagingFile);
            }
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot publish QGIS project " + projectName, e);
        }
        return "/io/data/" + projectName + ".qgs";
    }
}
