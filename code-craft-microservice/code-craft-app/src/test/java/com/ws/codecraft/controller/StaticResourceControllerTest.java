package com.ws.codecraft.controller;

import com.ws.codecraft.config.CodeProjectProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StaticResourceControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void serveStaticResource_shouldUseConfiguredOutputRootDir() throws IOException {
        Path projectDir = Files.createDirectories(tempDir.resolve("demo-app"));
        Files.writeString(projectDir.resolve("index.html"), "<html>ok</html>");

        CodeProjectProperties properties = new CodeProjectProperties();
        properties.setOutputRootDir(tempDir.toString());
        StaticResourceController controller = new StaticResourceController(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/static/demo-app/");
        request.setRequestURI("/api/static/demo-app/");

        ResponseEntity<Resource> response = controller.serveStaticResource(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/html; charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
        assertNotNull(response.getBody());
        assertEquals("index.html", response.getBody().getFilename());
    }

    @Test
    void serveStaticResource_shouldReturnNotFoundWhenFileMissing() {
        CodeProjectProperties properties = new CodeProjectProperties();
        properties.setOutputRootDir(tempDir.toString());
        StaticResourceController controller = new StaticResourceController(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/static/missing/app.js");
        request.setRequestURI("/api/static/missing/app.js");

        ResponseEntity<Resource> response = controller.serveStaticResource(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
