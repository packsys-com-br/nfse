package io.github.t3wv.nfse.nacional;

import io.github.t3wv.nfse.NFSeConfig;
import io.github.t3wv.nfse.NFSeLogger;
import io.github.t3wv.nfse.utils.NFSeHttpClient;
import io.github.t3wv.nfse.utils.NFSeHttpResponse;

import java.net.URI;

/**
 * Classe responsável por interagir com o serviço de download do DANFSe.
 */
public class WSDANFSe implements NFSeLogger {

    public static final String URL_BASE_PRODUCAO = "https://adn.nfse.gov.br";
    public static final String URL_BASE_HOMOLOGACAO = "https://adn.producaorestrita.nfse.gov.br";


    private final NFSeConfig config;

    public WSDANFSe(final NFSeConfig config) {
        this.config = config;
    }

    /**
     * Faz o download do DANFSe em PDF utilizando a chave de acesso da NFSe.
     *
     * @param chaveAcesso Chave de acesso da NFSe com 50 dígitos.
     * @return Array de bytes representando o arquivo PDF do DANFSe.
     * @throws Exception Se ocorrer um erro durante a requisição ou no processamento da resposta.
     */
    public byte[] downloadDANFSePdfByChaveAcesso(final String chaveAcesso) throws Exception {
        //normaliza a chave de acesso removendo quaisquer caracteres não numéricos
        final String chaveAcessoNormalizada = chaveAcesso.replaceAll("\\D", "");

        //valida o tamanho da chave de acesso, pois precisa ser exatamente 50 caracteres numericos
        if (!chaveAcessoNormalizada.matches("\\d{50}")) {
            throw new IllegalArgumentException("Chave de acesso da NFSe deve conter exatamente 50 dígitos numéricos!");
        }

        //monta a URL de consulta do DANFSe
        final URI url = new URI(String.format("%s/danfse/%s", this.config.isTeste() ? URL_BASE_HOMOLOGACAO : URL_BASE_PRODUCAO, chaveAcessoNormalizada));
        final NFSeHttpResponse<byte[]> response = new NFSeHttpClient(this.config).sendGetRequestAsBytes(url);
        this.getLogger().info("Response: {}", response);
        if (response.statusCode() != 200) {
            throw new Exception(String.format("Consulta de DANFSe '%s' retornou erro '%d'!", chaveAcessoNormalizada, response.statusCode()));
        } else {
            return response.body();
        }
    }
}
