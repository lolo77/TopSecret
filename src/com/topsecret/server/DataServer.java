package com.topsecret.server;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;

/**
 * @author Florent FRADET
 *
 * This little embedded web server is used
 * to display some secret content
 * without writing temporary files.
 *
 * Avoid letting fingerprints on the file system !
 */
public class DataServer {

    private HttpServer server;
    private int port;

    private byte[] data;

    public void buildServer() {
        // Start at port 8000
        port = 8000;

        // Try to bind to a port until one is available
        while (port < 0x10000) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                // Port is available, so exit the loop
                break;
            } catch (BindException e) {
                // Port is already in use, try the next one
                port++;
            } catch (IOException e) {
                // Server not built
                server = null;
                break;
            }
        }
    }


    public DataServer() throws IOException {
        buildServer();

        if (server != null) {
            // Set up the request handler
            server.createContext("/", exchange -> {
                if (data == null) {
                    exchange.sendResponseHeaders(404, 0);
                } else {
                    exchange.sendResponseHeaders(200, data.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(data);
                    os.close();
                }
            });
            server.start();
        }
    }

    public int getPort() {
        return port;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
