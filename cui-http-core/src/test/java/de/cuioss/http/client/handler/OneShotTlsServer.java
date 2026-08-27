/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.http.client.handler;

import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A minimal single-connection TLS server that presents the supplied certificate and answers the
 * first request with an empty {@code 200 OK}. Runs its accept loop on a background thread.
 */
final class OneShotTlsServer implements AutoCloseable {

    private final SSLServerSocket serverSocket;
    private final ExecutorService executor;

    private OneShotTlsServer(SSLServerSocket serverSocket, ExecutorService executor) {
        this.serverSocket = serverSocket;
        this.executor = executor;
    }

    static OneShotTlsServer start(HeldCertificate cert) throws IOException {
        SSLContext serverContext = new HandshakeCertificates.Builder()
                .heldCertificate(cert)
                .build()
                .sslContext();
        SSLServerSocket socket = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        OneShotTlsServer server = new OneShotTlsServer(socket, executor);
        executor.submit(server::serveOnce);
        return server;
    }

    private void serveOnce() {
        try (Socket client = serverSocket.accept();
             InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {
            // Drain the request line/headers (best effort) so the client can finish sending.
            byte[] buffer = new byte[1024];
            if (in.read(buffer) > 0) {
                // ignore contents; we only need the request to arrive
            }
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            // Expected when the handshake is rejected by the client (hostname mismatch).
        }
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
        serverSocket.close();
    }
}
