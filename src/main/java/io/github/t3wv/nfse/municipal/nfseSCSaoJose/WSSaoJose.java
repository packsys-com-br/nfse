package io.github.t3wv.nfse.municipal.nfseSCSaoJose;

import io.github.t3wv.nfse.NFSeLogger;
import io.github.t3wv.nfse.municipal.nfseSCSaoJose.arquivos.*;
import io.github.t3wv.nfse.transformers.NFSeBigDecimalTransformer;
import io.github.t3wv.nfse.transformers.NFSeRegistryMatcher;
import io.github.t3wv.nfse.utils.NFSeHttpResponse;
import io.github.t3wv.nfse.utils.NFSePersister;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WSSaoJose implements NFSeLogger {

    private static final String URL_PRODUCAO = "https://saojose.atende.net/atende.php?pg=rest&service=WNERestServiceNFSe&cidade=padrao";
    private static final int TIMEOUT_MILLIS = 10000;
    private final String credentials;

    public WSSaoJose(String login, String senha) {
        this.credentials = Base64.getEncoder().encodeToString(String.format("%s:%s", login, senha).getBytes());
    }

    public NFSeSCSaoJoseEmissaoRetorno enviarEmissaoNFSe(final NFSeSCSaoJoseEmissaoEnvio arquivoEmissao, final String filename) throws Exception {
        this.getLogger().info("Request emissão NFSe com xml: {}", arquivoEmissao.toXml());
        final NFSeHttpResponse<String> response = this.sendMultipartRequest(arquivoEmissao.toXml(), filename);
        final String responseBody = StringUtils.trimToEmpty(response.body());
        final int responseCode = response.statusCode();
        if (responseCode != 200) {
            throw new Exception(String.format("Emissão de NFSe retornou erro '%d'!", responseCode));
        } else {
            final NFSeRegistryMatcher registryMatcher = new NFSeRegistryMatcher();
            registryMatcher.bind(BigDecimal.class, new NFSeBigDecimalTransformer());
            this.getLogger().info("NFSe emitida: {}", responseBody);
            return new NFSePersister(true, registryMatcher).read(NFSeSCSaoJoseEmissaoRetorno.class, responseBody);
        }
    }

    public NFSeSCSaoJoseCancelamentoRetorno enviarCancelamentoNFSe(final NFSeSCSaoJoseCancelamentoEnvio arquivoCancelamento, final String filename) throws Exception {
        this.getLogger().info("Request cancelamento NFSe com xml: {}", arquivoCancelamento.toXml());
        final NFSeHttpResponse<String> response = this.sendMultipartRequest(arquivoCancelamento.toXml(), filename);
        final String responseBody = StringUtils.trimToEmpty(response.body()); // Trim no body porque o webservice retorna espaços em branco antes do XML
        final int responseCode = response.statusCode();
        if (responseCode != 200) {
            throw new Exception(String.format("Cancelamento de NFSe retornou erro '%d'!", responseCode));
        } else {
            final NFSeRegistryMatcher registryMatcher = new NFSeRegistryMatcher();
            registryMatcher.bind(BigDecimal.class, new NFSeBigDecimalTransformer());
            this.getLogger().info("NFSe cancelada: {}", responseBody);
            return new NFSePersister(true, registryMatcher).read(NFSeSCSaoJoseCancelamentoRetorno.class, responseBody);
        }
    }

    public NFSeSCSaoJoseCancelamentoRetorno enviarSolicitacaoCancelamentoNFSe(final NFSeSCSaoJoseCancelamentoSolicitacaoEnvio arquivoCancelamento, final String filename) throws Exception {
        this.getLogger().info("Request de solicitação de cancelamento NFSe com xml: {}", arquivoCancelamento.toXml());
        final NFSeHttpResponse<String> response = this.sendMultipartRequest(arquivoCancelamento.toXml(), filename);
        final String responseBody = StringUtils.trimToEmpty(response.body());
        final int responseCode = response.statusCode();
        if (responseCode != 200) {
            throw new Exception(String.format("Solicitação de cancelamento de NFSe retornou erro '%d'!", responseCode));
        } else {
            final NFSeRegistryMatcher registryMatcher = new NFSeRegistryMatcher();
            registryMatcher.bind(BigDecimal.class, new NFSeBigDecimalTransformer());
            this.getLogger().info("Solicitacao de cancelamento de NFSe registrada: {}", responseBody);
            return new NFSePersister(true, registryMatcher).read(NFSeSCSaoJoseCancelamentoRetorno.class, responseBody);
        }
    }

    public NFSeSCSaoJosePesquisaCodigoAutenticidadeRetorno consultaNFSeCodigoAutenticidade(final NFSeSCSaoJosePesquisaCodigoAutenticidadeEnvio arquivoPesquisa, final String filename) throws Exception {
        this.getLogger().info("Request de consulta de NFSe com xml: {}", arquivoPesquisa.toXml());
        final NFSeHttpResponse<String> response = this.sendMultipartRequest(arquivoPesquisa.toXml(), filename);
        final String responseBody = StringUtils.trimToEmpty(response.body());
        final int responseCode = response.statusCode();
        if (responseCode != 200) {
            throw new Exception(String.format("Consulta de NFSe por codigo de autenticidade retornou erro '%d'!", responseCode));
        } else {
            final NFSeRegistryMatcher registryMatcher = new NFSeRegistryMatcher();
            registryMatcher.bind(BigDecimal.class, new NFSeBigDecimalTransformer());
            this.getLogger().info("NFSe localizada: {}", responseBody);
            return new NFSePersister(true, registryMatcher).read(NFSeSCSaoJosePesquisaCodigoAutenticidadeRetorno.class, responseBody);
        }
    }

    private NFSeHttpResponse<String> sendMultipartRequest(final String xml, final String filename) throws Exception {
        final String boundary = String.format("----Boundary%d", System.currentTimeMillis());
        final String crlf = "\r\n";

        final byte[] multipartBody;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(String.format("--%s%sContent-Disposition: form-data; name=\"file\"; filename=\"%s\"%sContent-Type: text/xml; charset=UTF-8%s%s", boundary, crlf, filename, crlf, crlf, crlf).getBytes(StandardCharsets.UTF_8));
            baos.write(xml.getBytes(StandardCharsets.UTF_8));
            baos.write(String.format("%s--%s--%s", crlf, boundary, crlf).getBytes(StandardCharsets.UTF_8));
            multipartBody = baos.toByteArray();
        }

        final URL url = new URL(URL_PRODUCAO);
        final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Authorization", String.format("Basic %s", this.credentials));
        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(multipartBody);
        }

        final int statusCode = connection.getResponseCode();
        final InputStream is = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        final String body = is == null ? "" : new String(this.readFully(is), StandardCharsets.UTF_8);
        return new NFSeHttpResponse<String>(statusCode, body);
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
}
