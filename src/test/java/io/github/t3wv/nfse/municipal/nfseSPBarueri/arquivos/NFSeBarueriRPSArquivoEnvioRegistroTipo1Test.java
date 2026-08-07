package io.github.t3wv.nfse.municipal.nfseSPBarueri.arquivos;

import io.github.t3wv.nfse.municipal.nfseSPBarueri.enums.NFSeBarueriRetornoErros;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NFSeBarueriRPSArquivoEnvioRegistroTipo1Test {

    @Test
    void testFromLinhaAndToLinha() {
        final String linha = "14458481PMB00302602175865";
        final NFSeBarueriRPSArquivoEnvioRegistroTipo1 registro = new NFSeBarueriRPSArquivoEnvioRegistroTipo1().fromLinha(linha);
        assertEquals(NFSeBarueriRPSArquivoEnvioRegistroTipo1.TIPO_REGISTRO, registro.getTipoRegistro());
        assertEquals("4458481", registro.getInscricaoContribuinte());
        assertEquals("PMB003", registro.getVersaoLayout());
        assertEquals("02602175865", registro.getIdentificacaoRemessaContribuinte());
        assertEquals(linha, registro.toLinha());
    }

    @Test
    void testFromLinhaComErros() {
        final String linha = "14458481PMB00424480729157                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         901;";
        assertEquals(Arrays.asList(NFSeBarueriRetornoErros.ERRO_901), new NFSeBarueriRPSArquivoEnvioRegistroTipo1().fromLinha(linha).getErros());
    }
}