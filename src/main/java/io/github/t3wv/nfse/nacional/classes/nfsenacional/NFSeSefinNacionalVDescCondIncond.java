
package io.github.t3wv.nfse.nacional.classes.nfsenacional;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import java.math.BigDecimal;

/**
 * Grupo de informações relativas aos descontos condicionados e incondicionados
 */

@Root(name = "vDescCondIncond")
public class NFSeSefinNacionalVDescCondIncond {

    // B-155/B-156 no Manual de Integracao NFSe Nacional v1.01: ambos 0-1, opcionais e independentes
    @Element(name = "vDescIncond", required = false)
    protected BigDecimal valorDescontoIncondicionado;
    @Element(name = "vDescCond", required = false)
    protected BigDecimal valorDescontoCondicionado;

    /**
     * @return Valor monetário do desconto incondicionado (R$)
     */
    public BigDecimal getValorDescontoIncondicionado() {
        return valorDescontoIncondicionado;
    }

    /**
     * @param valorDescontoIncondicionado Valor monetário do desconto incondicionado (R$)
     */
    public NFSeSefinNacionalVDescCondIncond setValorDescontoIncondicionado(BigDecimal valorDescontoIncondicionado) {
        this.valorDescontoIncondicionado = valorDescontoIncondicionado;
        return this;
    }

    /**
     * @return Valor monetário do desconto condicionado (R$)
     */
    public BigDecimal getValorDescontoCondicionado() {
        return valorDescontoCondicionado;
    }

    /**
     * @param valorDescontoCondicionado Valor monetário do desconto condicionado (R$)
     */
    public NFSeSefinNacionalVDescCondIncond setValorDescontoCondicionado(BigDecimal valorDescontoCondicionado) {
        this.valorDescontoCondicionado = valorDescontoCondicionado;
        return this;
    }

}
