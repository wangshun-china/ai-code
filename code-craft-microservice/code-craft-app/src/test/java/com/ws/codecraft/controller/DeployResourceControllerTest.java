package com.ws.codecraft.controller;

import com.ws.codecraft.config.CodeProjectProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeployResourceControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void serveDeployResource_shouldRedirectBareDeployKeyToTrailingSlash() {
        CodeProjectProperties properties = new CodeProjectProperties();
        properties.setDeployRootDir(tempDir.toString());
        DeployResourceController controller = new DeployResourceController(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ABC123");
        request.setRequestURI("/ABC123");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/ABC123");

        ResponseEntity<Resource> response = controller.serveDeployResource("ABC123", request);

        assertEquals(HttpStatus.MOVED_PERMANENTLY, response.getStatusCode());
        assertEquals("/ABC123/", response.getHeaders().getFirst("Location"));
    }

    @Test
    void serveDeployResource_shouldServeFilesFromConfiguredDeployRoot() throws IOException {
        Path assetDir = Files.createDirectories(tempDir.resolve("ABC123").resolve("assets"));
        Files.writeString(assetDir.resolve("app.js"), "console.log('ok');");

        CodeProjectProperties properties = new CodeProjectProperties();
        properties.setDeployRootDir(tempDir.toString());
        DeployResourceController controller = new DeployResourceController(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ABC123/assets/app.js");
        request.setRequestURI("/ABC123/assets/app.js");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "/ABC123/assets/app.js");

        ResponseEntity<Resource> response = controller.serveDeployResource("ABC123", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/javascript; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
        assertNotNull(response.getBody());
        assertEquals("app.js", response.getBody().getFilename());
    }
}
