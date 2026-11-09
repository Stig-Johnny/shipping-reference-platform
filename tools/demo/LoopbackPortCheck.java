import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Checks the launcher's exact IPv4 loopback address without sending traffic to an existing service. */
class LoopbackPortCheck {
    public static void main(String[] args) {
        try {
            if (args.length != 1) { throw new IllegalArgumentException(); }
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) { throw new IllegalArgumentException(); }
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port));
            }
        } catch (IOException | IllegalArgumentException failure) {
            System.err.println("Demo port is unavailable; choose an unused loopback port");
            System.exit(2);
        }
    }
}
