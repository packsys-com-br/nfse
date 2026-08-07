package io.github.t3wv.nfse.nacional.classes.parametrosmunicipais.consulta;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Deserializador customizado para converter valores "1" em true e qualquer outro valor em false.
 */
class NFSeParametrosMunicipaisBooleanJsonDeserialize extends JsonDeserializer<Boolean> {

    private static final List<String> VERDADEIROS = Collections.unmodifiableList(Arrays.asList("1", "true", "sim", "s"));
    private static final List<String> FALSOS = Collections.unmodifiableList(Arrays.asList("0", "false", "nao", "não", "n"));

    @Override
    public Boolean deserialize(final JsonParser p, final DeserializationContext dc) throws IOException {
        final String valorSanitizado = StringUtils.trimToEmpty(p.getText());
        for (String verdadeiro : VERDADEIROS) {
            if (verdadeiro.equalsIgnoreCase(valorSanitizado)) {
                return true;
            }
        }
        for (String falso : FALSOS) {
            if (falso.equalsIgnoreCase(valorSanitizado)) {
                return false;
            }
        }
        throw new IOException(String.format("Valor booleano desconhecido: '%s'", valorSanitizado));
    }
}
