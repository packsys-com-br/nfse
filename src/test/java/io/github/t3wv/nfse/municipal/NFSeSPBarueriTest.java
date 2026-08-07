package io.github.t3wv.nfse.municipal;

import io.github.t3wv.nfse.NFSeConfig;
import io.github.t3wv.nfse.NFSeConfigTest;
import io.github.t3wv.nfse.NFSeLogger;
import io.github.t3wv.nfse.municipal.nfseSPBarueri.WSBarueri;
import io.github.t3wv.nfse.municipal.nfseSPBarueri.arquivos.*;
import io.github.t3wv.nfse.municipal.nfseSPBarueri.enums.*;
import io.github.t3wv.nfse.municipal.nfseSPBarueri.services.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Disabled
public class NFSeSPBarueriTest implements NFSeLogger {

    private static NFSeConfig config;

    @BeforeAll
    static void prepara() {
        config = new NFSeConfigTest();
    }

    @Test
    public void testeEmiteCancela() throws Exception {
        NFSeBarueriRPSArquivoEnvioRegistroTipo1 linha1 = new NFSeBarueriRPSArquivoEnvioRegistroTipo1().fromLinha("");
        NFSeBarueriRPSArquivoEnvioRegistroTipo2 linha2 = new NFSeBarueriRPSArquivoEnvioRegistroTipo2().fromLinha("");
        NFSeBarueriRPSArquivoEnvioRegistroTipo4 linha4 = new NFSeBarueriRPSArquivoEnvioRegistroTipo4().fromLinha("");
        NFSeBarueriRPSArquivoEnvioRegistroTipo9 linha9 = new NFSeBarueriRPSArquivoEnvioRegistroTipo9().fromLinha("");

        linha1.setIdentificacaoRemessaContribuinte("");

        linha2.setLocalPrestacaoServico(NFSeBarueriLocalPrestacaoServico.OUTROS);

        linha4.setCodigoCidadeLocalServicoPrestado(null);
        linha4.setCodigoPostalLocalServicoPrestadoExterior("666");
        linha4.setEstadoProvinciaRegiaoLocalServicoPrestadoExterior("sldfk");
        linha4.setPaisLocalServicoPrestado(NFSeBarueriPais.US);

        final NFSeBarueriRPSArquivoEnvio arquivo = new NFSeBarueriRPSArquivoEnvio()
            .addRegistro(linha1)
            .addRegistro(linha2)
            .addRegistro(linha4)
            .addRegistro(linha9);

        final WSBarueri wsBarueri = new WSBarueri(config);

        final NFSeBarueriLoteEnviarArquivoRequest arquivoRequest = new NFSeBarueriLoteEnviarArquivoRequest()
                .setCpfCnpjContribuinte("")
                .setInscricaoMunicipal("")
                .setApenasValidaArquivo(false)
                .setNomeArquivoRPS(String.format("RPS_%s.rem", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))))
                .setArquivoRPSBase64(arquivo.toBase64());
        Files.write(Paths.get(String.format("/tmp/%s.txt", arquivoRequest.getNomeArquivoRPS())), arquivo.toByteArray());

        // Envio o lote para emissão
        final NFSeBarueriLoteEnviarArquivoResponse responseEnvioEmissao = wsBarueri.loteEnviarArquivo(arquivoRequest);
        final String responseEnvioEmissaoProtocoloRemessa = responseEnvioEmissao.getResultado().getProtocoloRemessa();
        this.getLogger().info(String.format("Protocolo de remessa da emissão: %s", responseEnvioEmissaoProtocoloRemessa));
        Thread.sleep(1000);

        // Consulto o status do arquivo enviado
        final NFSeBarueriLoteStatusArquivoResponse responseStatusEmissao = wsBarueri
                .loteStatusArquivo(new NFSeBarueriLoteStatusArquivoRequest("", "", responseEnvioEmissao.getResultado().getProtocoloRemessa()));
        Files.write(Paths.get(String.format("/tmp/%s", arquivoRequest.getNomeArquivoRPS().replaceAll(".rem", String.format("_response_%s.xml", responseEnvioEmissao.getResultado().getProtocoloRemessa())))), responseStatusEmissao.toXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.getLogger().info("Arquivo enviado {}, com nome de retorno {}, com situacao {}: {}",
                responseStatusEmissao.getResult().getListaNfeArquivosRPS().getNomeArquivoOriginal(),
                responseStatusEmissao.getResult().getListaNfeArquivosRPS().getNomeArquivoRetorno(),
                responseStatusEmissao.getResult().getListaNfeArquivosRPS().getSituacaoArquivo().getCodigo(),
                responseStatusEmissao.getResult().getListaNfeArquivosRPS().getSituacaoArquivo().getDescricao());
        Thread.sleep(1000);

        // Baixo o arquivo de retorno da emissão
        final NFSeBarueriLoteBaixarArquivoResponse responseBaixarEmissao = wsBarueri
                .loteBaixarArquivo(new NFSeBarueriLoteBaixarArquivoRequest(
                        "",
                        "",
                        responseStatusEmissao.getResult().getListaNfeArquivosRPS().getNomeArquivoRetorno()));
        Files.write(Paths.get(String.format("/tmp/%s", arquivoRequest.getNomeArquivoRPS().replaceAll(".rem", String.format("_download_%s.xml", responseEnvioEmissao.getResultado().getProtocoloRemessa())))), responseBaixarEmissao.toXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        if (!"OK200".equals(responseBaixarEmissao.getResultado().getListaMensagemRetorno().getCodigo())) {
            throw new IllegalStateException(String.format("%s - %s - %s",
                    responseBaixarEmissao.getResultado().getListaMensagemRetorno().getCodigo(),
                    responseBaixarEmissao.getResultado().getListaMensagemRetorno().getMensagem(),
                    responseBaixarEmissao.getResultado().getListaMensagemRetorno().getCorrecao()));
        }

        final NFSeBarueriLoteBaixarArquivoResult resultadoEmissao = responseBaixarEmissao.getResultado();
        Files.write(Paths.get(String.format("/tmp/%s", arquivoRequest.getNomeArquivoRPS().replaceAll(".rem", String.format("_resultado_%s.txt", responseEnvioEmissao.getResultado().getProtocoloRemessa())))), Base64.getDecoder().decode(resultadoEmissao.getArquivoRPSBase64()));
        if (!"OK200".equals(resultadoEmissao.getListaMensagemRetorno().getCodigo())) {
            throw new IllegalStateException(String.format("%s - %s - %s",
                    resultadoEmissao.getListaMensagemRetorno().getCodigo(),
                    resultadoEmissao.getListaMensagemRetorno().getMensagem(),
                    resultadoEmissao.getListaMensagemRetorno().getCorrecao()));
        }

        if (NFSeBarueriArquivoRetornoSituacaoArquivo.ARQUIVO_COM_ERROS.equals(responseStatusEmissao.getResult().getListaNfeArquivosRPS().getSituacaoArquivo())) {
            final NFSeBarueriRPSArquivoEnvio arquivoRemessaComErros = resultadoEmissao.getArquivoEnvioComErros();
            for (final NFSeBarueriRetornoErros erro : arquivoRemessaComErros.getErros()) {
                this.getLogger().error("Erro no arquivo de remessa - Código: {}, Descrição: {}, Solução: {}",
                        erro.getCodigo(),
                        erro.getDescricao(),
                        erro.getSolucao());
            }
        } else {
            final NFSeBarueriRPSArquivoRetorno arquivoRetorno = resultadoEmissao.getArquivoRetorno();
            for (NFSeBarueriRPSArquivoRetornoRegistro registro : arquivoRetorno.getRegistros()) {
                if (NFSeBarueriRPSArquivoRetornoRegistroTipo2.TIPO_REGISTRO.equals(registro.getTipoRegistro())) {
                    final NFSeBarueriRPSArquivoRetornoRegistroTipo2 registroTipo2 = (NFSeBarueriRPSArquivoRetornoRegistroTipo2) registro;
                    final String serie = registroTipo2.getSerieNFe();
                    final Long numero = registroTipo2.getNumeroNFe();
                    final LocalDate emissao = registroTipo2.getDataNFe();
                    final String rps = registroTipo2.getNumeroRPS();
                    final String tomadorDocumento = registroTipo2.getTomadorDocumento();
                    final String codigoAtenticidade = registroTipo2.getCodigoAutenticidade();
                    //final var chaveNFSeNacional = registroTipo2.getChaveAcessoNFSeNacional();

                    //salva o pdf da emissao
                    try (final InputStream in = new URI(String.format("http://testeeiss.barueri.sp.gov.br/nfe/wfimagemnota.aspx?codigoautenticidade=%s&numdoc=%s", codigoAtenticidade, tomadorDocumento)).toURL().openStream()) {
                        this.getLogger().debug("Download PDF nota servico Barueri: {} {}", codigoAtenticidade, tomadorDocumento);
                        Files.write(Paths.get(String.format("/tmp/%s_%s_emitida.pdf", rps, numero)), readAllBytes(in));
                    }

                    //inicia o cancelamento
                    for (NFSeBarueriRPSArquivoEnvioRegistro arquivoRegistro : arquivo.getRegistros()) {
                        if (NFSeBarueriRPSArquivoEnvioRegistroTipo1.TIPO_REGISTRO.equals(arquivoRegistro.getTipoRegistro())) {
                            final NFSeBarueriRPSArquivoEnvioRegistroTipo1 rpsParaCancelamento = (NFSeBarueriRPSArquivoEnvioRegistroTipo1) arquivoRegistro;
                            rpsParaCancelamento.setIdentificacaoRemessaContribuinte(String.format("%s%s", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd")), LocalTime.now().toSecondOfDay()));
                        }
                        if (NFSeBarueriRPSArquivoEnvioRegistroTipo2.TIPO_REGISTRO.equals(arquivoRegistro.getTipoRegistro())) {
                            final NFSeBarueriRPSArquivoEnvioRegistroTipo2 rpsParaCancelamento = (NFSeBarueriRPSArquivoEnvioRegistroTipo2) arquivoRegistro;
                            rpsParaCancelamento
                                    .setNumeroRPS(0L)
                                    .setSituacaoRPS(NFSeBarueriSituacao.CANCELADO)
                                    .setMotivoCancelamento(NFSeBarueriMotivoCancelamento.CANCELAMENTO)
                                    .setNumeroNfeASerCanceladaOuSubstituida(String.valueOf(numero))
                                    .setSerieNfeASerCanceladaOuSubstituida(serie)
                                    .setDataEmissaoNFeASerCanceladaOuSubstituida(emissao)
                                    .setDescricaoCancelamento("Teste de cancelamento");
                        }
                    }

                    //refaz a request
                    final NFSeBarueriLoteEnviarArquivoRequest cancelamentoRequest = new NFSeBarueriLoteEnviarArquivoRequest()
                            .setCpfCnpjContribuinte("03918609000647")
                            .setInscricaoMunicipal("4458481")
                            .setApenasValidaArquivo(false)
                            .setNomeArquivoRPS(String.format("RPS_%s_cancelamento.rem", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))))
                            .setArquivoRPSBase64(arquivo.toBase64());
                    Files.write(Paths.get(String.format("/tmp/%s.txt", cancelamentoRequest.getNomeArquivoRPS())), arquivo.toByteArray());

                    // Envio o lote para emissão
                    final NFSeBarueriLoteEnviarArquivoResponse cancelamentoEnvioResponse = wsBarueri.loteEnviarArquivo(cancelamentoRequest);
                    final String cancelamentoEnvioResponseProtocoloRemessa = cancelamentoEnvioResponse.getResultado().getProtocoloRemessa();
                    this.getLogger().info(String.format("Protocolo de remessa do cancelamento: %s", cancelamentoEnvioResponseProtocoloRemessa));
                    Thread.sleep(1000);

                    // Consulto o status do arquivo enviado
                    final NFSeBarueriLoteStatusArquivoResponse cancelamentoStatusResponse = wsBarueri.loteStatusArquivo(new NFSeBarueriLoteStatusArquivoRequest("4458481", "03918609000647", cancelamentoEnvioResponse.getResultado().getProtocoloRemessa()));
                    Files.write(Paths.get(String.format("/tmp/%s", cancelamentoRequest.getNomeArquivoRPS().replaceAll(".rem", String.format("_response_%s.xml", cancelamentoEnvioResponse.getResultado().getProtocoloRemessa())))), cancelamentoStatusResponse.toXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    this.getLogger().info("Arquivo enviado {}, com nome de retorno {}, com situacao {}: {}",
                            cancelamentoStatusResponse.getResult().getListaNfeArquivosRPS().getNomeArquivoOriginal(),
                            cancelamentoStatusResponse.getResult().getListaNfeArquivosRPS().getNomeArquivoRetorno(),
                            cancelamentoStatusResponse.getResult().getListaNfeArquivosRPS().getSituacaoArquivo().getCodigo(),
                            cancelamentoStatusResponse.getResult().getListaNfeArquivosRPS().getSituacaoArquivo().getDescricao());
                    Thread.sleep(1000);

                    // Baixo o arquivo de retorno da emissão
                    final NFSeBarueriLoteBaixarArquivoResponse responseBaixarCancelamento = wsBarueri
                            .loteBaixarArquivo(new NFSeBarueriLoteBaixarArquivoRequest(
                                    "4458481",
                                    "03918609000647",
                                    cancelamentoStatusResponse.getResult().getListaNfeArquivosRPS().getNomeArquivoRetorno()));
                    Files.write(Paths.get(String.format("/tmp/%s", cancelamentoRequest.getNomeArquivoRPS().replaceAll(".rem", String.format("_download_%s.xml", cancelamentoEnvioResponse.getResultado().getProtocoloRemessa())))), responseBaixarCancelamento.toXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (!"OK200".equals(responseBaixarCancelamento.getResultado().getListaMensagemRetorno().getCodigo())) {
                        throw new IllegalStateException(String.format("%s - %s - %s",
                                responseBaixarCancelamento.getResultado().getListaMensagemRetorno().getCodigo(),
                                responseBaixarCancelamento.getResultado().getListaMensagemRetorno().getMensagem(),
                                responseBaixarCancelamento.getResultado().getListaMensagemRetorno().getCorrecao()));
                    }

                    final NFSeBarueriLoteBaixarArquivoResult resultadoCancelamento = responseBaixarCancelamento.getResultado();
                    Files.write(Paths.get(String.format("/tmp/%s", cancelamentoRequest.getNomeArquivoRPS().replaceAll(".rem", String.format("_resultado_%s.txt", cancelamentoEnvioResponse.getResultado().getProtocoloRemessa())))), Base64.getDecoder().decode(resultadoCancelamento.getArquivoRPSBase64()));
                    if (!"OK200".equals(resultadoCancelamento.getListaMensagemRetorno().getCodigo())) {
                        throw new IllegalStateException(String.format("%s - %s - %s",
                                resultadoCancelamento.getListaMensagemRetorno().getCodigo(),
                                resultadoCancelamento.getListaMensagemRetorno().getMensagem(),
                                resultadoCancelamento.getListaMensagemRetorno().getCorrecao()));
                    }

                    if (NFSeBarueriArquivoRetornoSituacaoArquivo.ARQUIVO_COM_ERROS.equals(cancelamentoStatusResponse.getResult().getListaNfeArquivosRPS().getSituacaoArquivo())) {
                        final NFSeBarueriRPSArquivoEnvio arquivoRemessaComErros = resultadoCancelamento.getArquivoEnvioComErros();
                        for (final NFSeBarueriRetornoErros erro : arquivoRemessaComErros.getErros()) {
                            this.getLogger().error("Erro no arquivo de remessa - Código: {}, Descrição: {}, Solução: {}",
                                    erro.getCodigo(),
                                    erro.getDescricao(),
                                    erro.getSolucao());
                        }
                    } else {
                        //salva o pdf do cancelamento
                        try (final InputStream in = new URI(String.format("http://testeeiss.barueri.sp.gov.br/nfe/wfimagemnota.aspx?codigoautenticidade=%s&numdoc=%s", codigoAtenticidade, tomadorDocumento)).toURL().openStream()) {
                            this.getLogger().debug("Download PDF nota servico Barueri: {} {}", codigoAtenticidade, tomadorDocumento);
                            Files.write(Paths.get(String.format("/tmp/%s_%s_cancelada.pdf", rps, numero)), readAllBytes(in));
                        }
                    }
                }
            }
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }
}
