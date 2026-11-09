import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Real occupied-port regressions: the launcher must refuse before Gradle or its Java server starts. */
class LoopbackPortCheckTest {
    public static void main(String[] args) throws Exception {
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        HttpServer http = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        http.createContext("/", exchange -> {
            byte[] body = "An existing synthetic server".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) { response.write(body); }
        });
        http.start();
        try {
            refuses("occupied HTTP port", http.getAddress().getPort());
        } finally {
            http.stop(0);
        }
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(loopback, 0));
            refuses("occupied non-HTTP port", socket.getLocalPort());
        }
    }

    private static void refuses(String label, int port) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", "tools/demo/run-attended-demo.sh", "memory",
                Integer.toString(port), "tools/demo/fixture").redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().remove("SPRING_DATASOURCE_URL");
        builder.environment().remove("SPRING_DATASOURCE_USERNAME");
        builder.environment().remove("SPRING_DATASOURCE_PASSWORD");
        Path output = java.nio.file.Files.createTempFile("demo-port-refusal-", ".txt");
        builder.redirectOutput(output.toFile());
        Process process = builder.start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new AssertionError(label + ": launcher did not terminate");
            }
            String text = java.nio.file.Files.readString(output);
            if (process.exitValue() != 2 || !text.contains("Demo port is unavailable")
                    || text.contains("Demo ready:") || text.contains("Task :") || text.contains("BUILD ")) {
                throw new AssertionError(label + ": expected refusal before build/readiness; exit "
                        + process.exitValue());
            }
            System.out.println("PASS: " + label + " refused before build/readiness");
        } finally {
            process.descendants().forEach(child -> child.destroyForcibly());
            if (process.isAlive()) { process.destroyForcibly(); }
            java.nio.file.Files.deleteIfExists(output);
        }
    }
}
