package io.github.t3wv.nfse.utils;

import io.github.t3wv.nfse.NFSeConfig;
import io.github.t3wv.nfse.NFSeLogger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Classe utilitária para realizar requisições HTTP (GET e POST) para serviços de NFSe.
 */
public class NFSeHttpClient implements NFSeLogger {

    public static final String PROTOCOL = "TLSv1.2";

    private final NFSeConfig config;

    public NFSeHttpClient(final NFSeConfig config) {
        this.config = config;
    }

    /**
     * Método que realiza uma requisição GET para a uri especificada, retornando o corpo como String.
     *
     * @param uri URI para a qual a requisição será feita.
     * @return Objeto {@link NFSeHttpResponse} contendo a resposta da requisição.
     * @throws Exception Se ocorrer um erro durante a requisição.
     */
    public NFSeHttpResponse<String> sendGetRequest(final URI uri) throws Exception {
        final NFSeHttpResponse<byte[]> response = this.sendGetRequestAsBytes(uri);
        return new NFSeHttpResponse<String>(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
    }

    /**
     * Método que realiza uma requisição GET para a uri especificada, retornando o corpo como array de bytes.
     *
     * @param uri URI para a qual a requisição será feita.
     * @return Objeto {@link NFSeHttpResponse} contendo a resposta da requisição.
     * @throws Exception Se ocorrer um erro durante a requisição.
     */
    public NFSeHttpResponse<byte[]> sendGetRequestAsBytes(final URI uri) throws Exception {
        this.getLogger().debug("Realizando GET em: {}", uri.toString());
        final HttpsURLConnection connection = this.openConnection(uri, "GET", config.getHttpGetTimeoutSegundos());
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        return this.execute(connection);
    }

    public NFSeHttpResponse<String> sendPostRequest(final URI uri, final String body) throws Exception {
        return this.sendPostRequest(uri, Collections.singletonMap("Content-Type", "application/json; charset=utf-8"), body);
    }

    public NFSeHttpResponse<String> sendPostRequest(final URI uri, final Map<String, String> headers, final String body) throws Exception {
        this.getLogger().debug("Realizando POST em: {}", uri.toString());
        final HttpsURLConnection connection = this.openConnection(uri, "POST", config.getHttpPostTimeoutSegundos());
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        connection.setDoOutput(true);
        try (final OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        final NFSeHttpResponse<byte[]> response = this.execute(connection);
        return new NFSeHttpResponse<String>(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
    }

    private HttpsURLConnection openConnection(final URI uri, final String method, final int timeoutSegundos) throws Exception {
        final URL url = uri.toURL();
        final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setSSLSocketFactory(this.getSslContext().getSocketFactory());
        connection.setConnectTimeout(timeoutSegundos * 1000);
        connection.setReadTimeout(timeoutSegundos * 1000);
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private NFSeHttpResponse<byte[]> execute(final HttpsURLConnection connection) throws IOException {
        final int statusCode = connection.getResponseCode();
        final InputStream is = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        final byte[] body = is == null ? new byte[0] : this.readFully(is);
        return new NFSeHttpResponse<byte[]>(statusCode, body);
    }

    private byte[] readFully(final InputStream is) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }

    private SSLContext getSslContext() throws Exception {
        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(config.getKeyStoreCertificado(), config.getCertificadoSenha().toCharArray());
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(config.getKeyStoreCadeia());
        final SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}
