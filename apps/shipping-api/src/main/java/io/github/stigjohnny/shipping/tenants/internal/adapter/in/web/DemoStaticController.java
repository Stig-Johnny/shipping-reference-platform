package io.github.stigjohnny.shipping.tenants.internal.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Reads only regular files whose real path is below the configured, operator-owned build directory. */
@RestController
@Profile("demo")
@ConditionalOnProperty("shipping.demo.static-directory")
class DemoStaticController {
    private final Path root;

    DemoStaticController(@Value("${shipping.demo.static-directory}") String directory) throws IOException {
        root = Path.of(directory).toRealPath();
        if (!Files.isDirectory(root)) { throw new IllegalArgumentException("Demo static directory must be a directory"); }
    }

    @GetMapping({"/", "/{*path}"})
    ResponseEntity<byte[]> page(HttpServletRequest request) throws IOException, NoResourceFoundException {
        String path = request.getServletPath();
        if (path.equals("/api") || path.startsWith("/api/") || path.contains("%") || path.contains("\\")) {
            throw missing();
        }
        Path candidate = root.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) { throw missing(); }
        Path real = candidate.toRealPath();
        if (!real.startsWith(root)) { throw missing(); }
        MediaType type = MediaTypeFactory.getMediaType(real.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(Files.readAllBytes(real));
    }

    private static NoResourceFoundException missing() {
        return new NoResourceFoundException(HttpMethod.GET, "", "");
    }
}
