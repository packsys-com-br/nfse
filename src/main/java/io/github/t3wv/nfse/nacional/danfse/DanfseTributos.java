package io.github.t3wv.nfse.nacional.danfse;

import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalRegimeTributarioRegimeEspecialTributacao;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalTribMunicipalTributacaoISSQN;

import java.math.BigDecimal;

import static io.github.t3wv.nfse.nacional.danfse.DanfseCanvas.CM;
import static io.github.t3wv.nfse.nacional.danfse.DanfseCanvas.LINHA;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalBeneficioMunicipal;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalExigSuspensa;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSe;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBS;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBSTotais;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBSTotaisCBS;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBSTotaisIBS;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBSValores;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBSValoresFed;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBSValoresMun;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfNFSeIBSCBSValoresUF;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfoDedRed;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfoIBSCBS;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfoIBSCBSInfoTributosGIBSCBS;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfoPrestador;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalInfoValores;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalTribFederal;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalTribMunicipal;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalTribOutrosPisCofins;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalTribOutrosPisCofinsTipoRetencao;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalVDescCondIncond;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalVServPrest;
import io.github.t3wv.nfse.nacional.classes.nfsenacional.NFSeSefinNacionalValoresNFSe;
import java.time.LocalDate;

/**
 * Blocos da tributação municipal (ISSQN), da tributação federal (exceto CBS), da tributação IBS/CBS e do valor total da NFS-e
 * (itens 2.1.8 a 2.1.11 da NT 008).
 */
final class DanfseTributos {

    static final String NAO_SUJEITA_AO_ISSQN = "TRIBUTAÇÃO MUNICIPAL (ISSQN) - OPERAÇÃO NÃO SUJEITA AO ISSQN";

    /** Passo das linhas do bloco do valor total (0,67 cm de campo, como na coluna "Sup." do item 2.4.5). */
    private static final float LINHA_TOTAL = 0.685f * CM;
    /** Último ano de competência em que se imprime a linha do PIS/COFINS de apuração própria (nota 6 do item 2.4.5). */
    private static final int ULTIMO_ANO_PIS_COFINS = 2026;

    /**
     * Classe utilitária, não deve ser instanciada.
     */
    private DanfseTributos() {
    }

    /**
     * @param nota NFS-e
     * @return altura dos quatro blocos, em pontos, já sem as linhas e os blocos suprimidos
     */
    static float altura(final DanfseNota nota) {
        final float issqn = naoSujeitaAoIssqn(nota) ? DanfseParticipantes.ALTURA_SUPRIMIDO
                : LINHA * (2 + (temRegimeOuSuspensao(nota) ? 1 : 0) + (temBeneficioOuDeducao(nota) ? 1 : 0));
        final float federal = LINHA * (temPisCofinsDeApuracaoPropria(nota) ? 2 : 1);
        return issqn + federal + 4 * LINHA + 2 * LINHA_TOTAL;
    }

    /**
     * Desenha os quatro blocos a partir do cursor; ao final, o cursor fica no topo das informações complementares.
     *
     * @param canvas área de desenho
     * @param nota NFS-e
     */
    static void desenhar(final DanfseCanvas canvas, final DanfseNota nota) {
        municipal(canvas, nota);
        federal(canvas, nota);
        ibsCbs(canvas, nota);
        total(canvas, nota);
    }

    /**
     * Operações sem incidência do ISSQN levam só a mensagem da nota 4 do item 2.4.5 no lugar do bloco.
     */
    static boolean naoSujeitaAoIssqn(final DanfseNota nota) {
        final NFSeSefinNacionalTribMunicipal tribMun = nota.tribMun();
        return tribMun == null || tribMun.getTributacaoISSQN() == NFSeSefinNacionalTribMunicipalTributacaoISSQN.NAO_INCIDENCIA;
    }

    private static void municipal(final DanfseCanvas canvas, final DanfseNota nota) {
        if (naoSujeitaAoIssqn(nota)) {
            canvas.blocoSuprimido(NAO_SUJEITA_AO_ISSQN, DanfseParticipantes.ALTURA_SUPRIMIDO);
            canvas.avancar(DanfseParticipantes.ALTURA_SUPRIMIDO);
            return;
        }
        final NFSeSefinNacionalInfNFSe inf = nota.inf();
        final NFSeSefinNacionalTribMunicipal tribMun = nota.tribMun();
        final NFSeSefinNacionalValoresNFSe valores = inf.getValores();

        canvas.divisoria();
        canvas.tituloBloco("TRIBUTAÇÃO MUNICIPAL (ISSQN)");
        canvas.campo(1, 1, "Tipo de Tributação do ISSQN", DanfseFormatador.ouTraco(DanfseFormatador.limitar(
                tribMun.getTributacaoISSQN() == null ? null : tribMun.getTributacaoISSQN().getDescricao(), 21)));
        canvas.campo(2, 2, "Município / Sigla UF / País de Incidência do ISSQN", DanfseFormatador.limitar(
                DanfseServico.local(inf.getLocalIncidenciaISSQN(), inf.getCodigoLocalIncidenciaISSQN(), tribMun.getCodigoPais()), 42));
        canvas.avancar(LINHA);

        // linhas suprimidas quando nenhum campo delas tem dados no XML (nota 5 do item 2.4.5)
        if (temRegimeOuSuspensao(nota)) {
            final NFSeSefinNacionalExigSuspensa suspensao = tribMun.getExigibilidadeSuspensa();
            canvas.campo(0, 1, "Regime Especial de Tributação do ISSQN", DanfseFormatador.ouTraco(DanfseFormatador.limitar(descricaoRegimeEspecial(nota), 27)));
            canvas.campo(1, 1, "Tipo de Imunidade do ISSQN", DanfseFormatador.ouTraco(DanfseFormatador.limitar(
                    tribMun.getTipoImunidade() == null ? null : tribMun.getTipoImunidade().getDescricao(), 40)));
            canvas.campo(2, 1, "Suspensão da Exigibilidade do ISSQN", DanfseFormatador.ouTraco(DanfseFormatador.limitar(
                    suspensao == null || suspensao.getTipoSuspensao() == null ? null : suspensao.getTipoSuspensao().getDescricao(), 40)));
            canvas.campo(3, 1, "Número Processo Suspensão", DanfseFormatador.ouTraco(DanfseFormatador.limitar(
                    suspensao == null ? null : suspensao.getNumeroProcesso(), 30)));
            canvas.avancar(LINHA);
        }
        if (temBeneficioOuDeducao(nota)) {
            canvas.campo(0, 1, "Benefício Municipal", DanfseFormatador.ouTraco(DanfseFormatador.limitar(
                    valores == null || valores.getTpBM() == null ? null : valores.getTpBM().getDescricao(), 40)));
            canvas.campo(1, 1, "Cálculo do BM", DanfseFormatador.valor(calculoBeneficioMunicipal(nota)));
            canvas.campo(2, 1, "Total Deduções/Reduções", DanfseFormatador.valor(totalDeducoesReducoes(nota)));
            canvas.campo(3, 1, "Desconto Incondicionado", DanfseFormatador.valor(descontoIncondicionado(nota)));
            canvas.avancar(LINHA);
        }

        canvas.campo(0, 1, "BC ISSQN", DanfseFormatador.valor(valores == null ? null : valores.getVBC()));
        canvas.campo(1, 1, "Alíquota Aplicada", DanfseFormatador.percentual(valores == null ? null : valores.getPAliqAplic()));
        canvas.campo(2, 1, "Retenção do ISSQN", DanfseFormatador.ouTraco(DanfseFormatador.limitar(
                tribMun.getTipoRetencaoISSQN() == null ? null : tribMun.getTipoRetencaoISSQN().getDescricao(), 25)));
        canvas.campo(3, 1, "ISSQN Apurado", DanfseFormatador.valor(valores == null ? null : valores.getVISSQN()));
        canvas.avancar(LINHA);
    }

    private static String descricaoRegimeEspecial(final DanfseNota nota) {
        final NFSeSefinNacionalInfoPrestador prestador = nota.dps().getPrestador();
        final NFSeSefinNacionalRegimeTributarioRegimeEspecialTributacao regime = prestador == null || prestador.getRegimeTributario() == null ? null : prestador.getRegimeTributario().getRegimeEspecialTributacao();
        return regime == null ? null : regime.getDescricao();
    }

    private static boolean temRegimeOuSuspensao(final DanfseNota nota) {
        final NFSeSefinNacionalTribMunicipal tribMun = nota.tribMun();
        final NFSeSefinNacionalInfoPrestador prestador = nota.dps().getPrestador();
        final NFSeSefinNacionalRegimeTributarioRegimeEspecialTributacao regime = prestador == null || prestador.getRegimeTributario() == null ? null : prestador.getRegimeTributario().getRegimeEspecialTributacao();
        // "0 - Nenhum" nao e dado de regime especial: a linha do modelo do Sistema Nacional some nesse caso
        final boolean temRegime = regime != null && regime != NFSeSefinNacionalRegimeTributarioRegimeEspecialTributacao.NENHUM;
        final boolean temImunidadeOuSuspensao = tribMun != null && (tribMun.getTipoImunidade() != null || tribMun.getExigibilidadeSuspensa() != null);
        return temRegime || temImunidadeOuSuspensao;
    }

    /**
     * Valores zerados (como o vCalcReeRepRes 0.00 que o Sistema Nacional sempre informa) não contam como dado da linha.
     */
    private static boolean temBeneficioOuDeducao(final DanfseNota nota) {
        final NFSeSefinNacionalValoresNFSe valores = nota.inf().getValores();
        final boolean temBeneficio = valores != null && valores.getTpBM() != null;
        return temBeneficio || diferenteDeZero(calculoBeneficioMunicipal(nota)) || diferenteDeZero(totalDeducoesReducoes(nota))
                || diferenteDeZero(descontoIncondicionado(nota));
    }

    private static boolean diferenteDeZero(final BigDecimal valor) {
        return valor != null && valor.signum() != 0;
    }

    /**
     * Cálculo do benefício municipal: vCalcBM ou, sem ele, vRedBCBM (item 2.4.5).
     */
    private static BigDecimal calculoBeneficioMunicipal(final DanfseNota nota) {
        final NFSeSefinNacionalValoresNFSe valores = nota.inf().getValores();
        if (valores != null && valores.getVCalcBM() != null) {
            return valores.getVCalcBM();
        }
        final NFSeSefinNacionalTribMunicipal tribMun = nota.tribMun();
        final NFSeSefinNacionalBeneficioMunicipal beneficio = tribMun == null ? null : tribMun.getBeneficioMunicipal();
        return beneficio == null ? null : beneficio.getValorReducaoBaseCalculoBeneficioMunicipal();
    }

    /**
     * Total de deduções e reduções: vCalcDR (ou, sem ele, vDR) mais vCalcReeRepRes do IBS/CBS (item 2.4.5).
     */
    private static BigDecimal totalDeducoesReducoes(final DanfseNota nota) {
        final NFSeSefinNacionalValoresNFSe valores = nota.inf().getValores();
        final NFSeSefinNacionalInfoDedRed deducao = nota.dps().getValores() == null ? null : nota.dps().getValores().getValoresDeducaoBaseCalculo();
        final BigDecimal calculado = valores == null ? null : valores.getVCalcDR();
        final BigDecimal deducaoInformada = calculado != null ? calculado : deducao == null ? null : deducao.getValorMonetarioPadrao();
        final BigDecimal reeRepRes = nota.ibsCbsNota() == null || nota.ibsCbsNota().getValores() == null ? null : nota.ibsCbsNota().getValores().getvCalcReeRepRes();
        if (deducaoInformada == null) {
            return reeRepRes;
        }
        return reeRepRes == null ? deducaoInformada : deducaoInformada.add(reeRepRes);
    }

    private static BigDecimal descontoIncondicionado(final DanfseNota nota) {
        final NFSeSefinNacionalInfoValores valores = nota.dps().getValores();
        final NFSeSefinNacionalVDescCondIncond descontos = valores == null ? null : valores.getValoresDescontosCondicionadosEIncondicionados();
        return descontos == null ? null : descontos.getValorDescontoIncondicionado();
    }

    private static BigDecimal descontoCondicionado(final DanfseNota nota) {
        final NFSeSefinNacionalInfoValores valores = nota.dps().getValores();
        final NFSeSefinNacionalVDescCondIncond descontos = valores == null ? null : valores.getValoresDescontosCondicionadosEIncondicionados();
        return descontos == null ? null : descontos.getValorDescontoCondicionado();
    }

    private static void federal(final DanfseCanvas canvas, final DanfseNota nota) {
        final NFSeSefinNacionalTribFederal tribFed = nota.tribFed();
        final NFSeSefinNacionalTribOutrosPisCofins pisCofins = nota.pisCofins();
        canvas.divisoria();
        canvas.tituloBloco("TRIBUTAÇÃO FEDERAL (EXCETO CBS)");
        canvas.campo(1, 1, "IRRF", DanfseFormatador.valor(tribFed == null ? null : tribFed.getValorIRRF()));
        canvas.campo(2, 1, "Contribuição Previdenciária - Retida", DanfseFormatador.valor(tribFed == null ? null : tribFed.getValorCP()));
        canvas.campo(3, 1, "Contribuições Sociais - Retidas", DanfseFormatador.valor(contribuicoesSociaisRetidas(nota)));
        canvas.avancar(LINHA);
        if (temPisCofinsDeApuracaoPropria(nota)) {
            canvas.campo(0, 1, "PIS - Débito Apuração Própria", DanfseFormatador.valor(pisApuracaoPropria(nota)));
            canvas.campo(1, 1, "COFINS - Débito Apuração Própria", DanfseFormatador.valor(cofinsApuracaoPropria(nota)));
            canvas.campo(2, 2, "Descrição Contrib. Sociais - Retidas", DanfseFormatador.ouTraco(DanfseFormatador.limitar(
                    pisCofins == null || pisCofins.getTipoRetencao() == null ? null : pisCofins.getTipoRetencao().getDescricao(), 35)));
            canvas.avancar(LINHA);
        }
    }

    /**
     * A linha do PIS/COFINS de apuração própria só é impressa nas NFS-e com competência até o fim de 2026 (nota 6).
     */
    private static boolean temPisCofinsDeApuracaoPropria(final DanfseNota nota) {
        final LocalDate competencia = nota.dps().getDataInicioPrestacaoServico();
        return competencia == null || competencia.getYear() <= ULTIMO_ANO_PIS_COFINS;
    }

    /**
     * Contribuições sociais retidas: vRetCSLL mais o PIS (vPis) e a COFINS (vCofins) que o tipo de retenção indica como retidos.
     * Para o tpRetPisCofins 1 (PIS/COFINS Retidos) é a soma de vRetCSLL, vPis e vCofins pedida no item 2.4.5; a mesma regra é
     * aplicada, tributo a tributo, aos demais tipos com PIS ou COFINS retidos.
     */
    static BigDecimal contribuicoesSociaisRetidas(final DanfseNota nota) {
        final NFSeSefinNacionalTribFederal tribFed = nota.tribFed();
        final NFSeSefinNacionalTribOutrosPisCofins pisCofins = nota.pisCofins();
        final NFSeSefinNacionalTribOutrosPisCofinsTipoRetencao retencao = pisCofins == null ? null : pisCofins.getTipoRetencao();
        final BigDecimal pis = retencao != null && retencao.isPisRetido() ? pisCofins.getValorPIS() : null;
        final BigDecimal cofins = retencao != null && retencao.isCofinsRetido() ? pisCofins.getValorCofins() : null;
        final BigDecimal csll = tribFed == null ? null : tribFed.getValorCSLL();
        if (csll == null && pis == null && cofins == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        if (csll != null) {
            total = total.add(csll);
        }
        if (pis != null) {
            total = total.add(pis);
        }
        if (cofins != null) {
            total = total.add(cofins);
        }
        return total;
    }

    /**
     * PIS de apuração própria: 0,00 quando o tipo de retenção indica o PIS retido (o valor vai para as contribuições retidas);
     * nos demais casos, vPis.
     */
    static BigDecimal pisApuracaoPropria(final DanfseNota nota) {
        final NFSeSefinNacionalTribOutrosPisCofins pisCofins = nota.pisCofins();
        if (pisCofins == null) {
            return null;
        }
        return pisCofins.getTipoRetencao() != null && pisCofins.getTipoRetencao().isPisRetido() ? BigDecimal.ZERO : pisCofins.getValorPIS();
    }

    /**
     * COFINS de apuração própria: 0,00 quando o tipo de retenção indica a COFINS retida; nos demais casos, vCofins.
     */
    static BigDecimal cofinsApuracaoPropria(final DanfseNota nota) {
        final NFSeSefinNacionalTribOutrosPisCofins pisCofins = nota.pisCofins();
        if (pisCofins == null) {
            return null;
        }
        return pisCofins.getTipoRetencao() != null && pisCofins.getTipoRetencao().isCofinsRetido() ? BigDecimal.ZERO : pisCofins.getValorCofins();
    }

    private static void ibsCbs(final DanfseCanvas canvas, final DanfseNota nota) {
        final NFSeSefinNacionalInfNFSeIBSCBS ibsCbs = nota.ibsCbsNota();
        final NFSeSefinNacionalInfNFSeIBSCBSValores valores = ibsCbs == null ? null : ibsCbs.getValores();
        final NFSeSefinNacionalInfNFSeIBSCBSValoresUF uf = valores == null ? null : valores.getUf();
        final NFSeSefinNacionalInfNFSeIBSCBSValoresMun mun = valores == null ? null : valores.getMun();
        final NFSeSefinNacionalInfNFSeIBSCBSValoresFed fed = valores == null ? null : valores.getFed();
        final NFSeSefinNacionalInfNFSeIBSCBSTotais totais = ibsCbs == null ? null : ibsCbs.getTotCIBS();
        final NFSeSefinNacionalInfNFSeIBSCBSTotaisIBS ibs = totais == null ? null : totais.getgIBS();
        final NFSeSefinNacionalInfNFSeIBSCBSTotaisCBS cbs = totais == null ? null : totais.getgCBS();
        final NFSeSefinNacionalInfoIBSCBS dps = nota.ibsCbsDps();
        final NFSeSefinNacionalInfoIBSCBSInfoTributosGIBSCBS tributos = dps == null || dps.getValores() == null || dps.getValores().getTrib() == null ? null : dps.getValores().getTrib().getgIBSCBS();

        canvas.divisoria();
        canvas.tituloBloco("TRIBUTAÇÃO IBS/CBS");
        canvas.campo(1, 1, "CST / cClassTrib", tributos == null ? DanfseFormatador.SEM_INFORMACAO
                : DanfseFormatador.limitar(DanfseFormatador.ouTraco(tributos.getCST()) + " / " + DanfseFormatador.ouTraco(tributos.getcClassTrib()), 12));
        final String indicador = ibsCbs == null && dps == null ? DanfseFormatador.SEM_INFORMACAO
                : DanfseFormatador.ouTraco(dps == null ? null : dps.getcIndOp()) + " / "
                + DanfseFormatador.ouTraco(ibsCbs == null ? null : ibsCbs.getcLocalidadeIncid()) + " / "
                + DanfseFormatador.ouTraco(ibsCbs == null ? null : ibsCbs.getxLocalidadeIncid()) + " / "
                + DanfseFormatador.ouTraco(DanfseFormatador.uf(ibsCbs == null ? null : ibsCbs.getcLocalidadeIncid()));
        canvas.campo(2, 2, "Indicador de Operação / Código IBGE Incidência / Município Incidência / Sigla UF", DanfseFormatador.limitar(indicador, 56));
        canvas.avancar(LINHA);

        canvas.campo(0, 1, "Exclusões e Reduções da Base de Cálculo", DanfseFormatador.valor(ibsCbs == null ? null : exclusoesEReducoes(nota)));
        canvas.campo(1, 1, "Base de Cálculo Após Exclusões e Reduções", DanfseFormatador.valor(valores == null ? null : valores.getvBC()));
        canvas.campo(2, 1, "Red. Alíquota IBS / Red. Alíquota CBS", ibsCbs == null ? DanfseFormatador.SEM_INFORMACAO
                : DanfseFormatador.percentual(uf == null ? null : uf.getpRedAliqUF()) + " / " + DanfseFormatador.percentual(mun == null ? null : mun.getpRedAliqMun())
                + " / " + DanfseFormatador.percentual(fed == null ? null : fed.getpRedAliqCBS()));
        canvas.campo(3, 1, "Alíquota - IBS UF / IBS Mun", ibsCbs == null ? DanfseFormatador.SEM_INFORMACAO
                : DanfseFormatador.percentual(uf == null ? null : uf.getpIBSUF()) + " / " + DanfseFormatador.percentual(mun == null ? null : mun.getpIBSMun()));
        canvas.avancar(LINHA);

        canvas.campo(0, 1, "Alíq. Efetiva Municipal - IBS", DanfseFormatador.percentual(mun == null ? null : mun.getpAliqEfetMun()));
        canvas.campo(1, 1, "Valor Apurado Municipal - IBS", DanfseFormatador.valor(ibs == null || ibs.getgIBSMunTot() == null ? null : ibs.getgIBSMunTot().getvIBSMun()));
        canvas.campo(2, 1, "Alíq. Efetiva Estadual - IBS", DanfseFormatador.percentual(uf == null ? null : uf.getpAliqEfetUF()));
        canvas.campo(3, 1, "Valor Apurado Estadual - IBS", DanfseFormatador.valor(ibs == null || ibs.getgIBSUFTot() == null ? null : ibs.getgIBSUFTot().getvIBSUF()));
        canvas.avancar(LINHA);

        canvas.campo(0, 1, "Valor Total Apurado - IBS", DanfseFormatador.valor(ibs == null ? null : ibs.getvIBSTot()));
        canvas.campo(1, 1, "Alíquota - CBS", DanfseFormatador.percentual(fed == null ? null : fed.getpCBS()));
        canvas.campo(2, 1, "Alíquota Efetiva - CBS", DanfseFormatador.percentual(fed == null ? null : fed.getpAliqEfetCBS()));
        canvas.campo(3, 1, "Valor Total Apurado - CBS", DanfseFormatador.valor(cbs == null ? null : cbs.getvCBS()));
        canvas.avancar(LINHA);
    }

    /**
     * Exclusões e reduções da base de cálculo do IBS/CBS: soma de vDescIncond, vCalcReeRepRes, vISSQN, vPis e vCofins
     * (item 2.4.5).
     */
    static BigDecimal exclusoesEReducoes(final DanfseNota nota) {
        final NFSeSefinNacionalValoresNFSe valores = nota.inf().getValores();
        final NFSeSefinNacionalTribOutrosPisCofins pisCofins = nota.pisCofins();
        final NFSeSefinNacionalInfNFSeIBSCBS ibsCbs = nota.ibsCbsNota();
        final BigDecimal descontoIncondicionado = descontoIncondicionado(nota);
        final BigDecimal reeRepRes = ibsCbs == null || ibsCbs.getValores() == null ? null : ibsCbs.getValores().getvCalcReeRepRes();
        final BigDecimal issqn = valores == null ? null : valores.getVISSQN();
        final BigDecimal pis = pisCofins == null ? null : pisCofins.getValorPIS();
        final BigDecimal cofins = pisCofins == null ? null : pisCofins.getValorCofins();
        if (descontoIncondicionado == null && reeRepRes == null && issqn == null && pis == null && cofins == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        if (descontoIncondicionado != null) {
            total = total.add(descontoIncondicionado);
        }
        if (reeRepRes != null) {
            total = total.add(reeRepRes);
        }
        if (issqn != null) {
            total = total.add(issqn);
        }
        if (pis != null) {
            total = total.add(pis);
        }
        if (cofins != null) {
            total = total.add(cofins);
        }
        return total;
    }

    private static void total(final DanfseCanvas canvas, final DanfseNota nota) {
        final NFSeSefinNacionalValoresNFSe valores = nota.inf().getValores();
        final NFSeSefinNacionalInfoValores valoresDps = nota.dps().getValores();
        final NFSeSefinNacionalVServPrest servico = valoresDps == null ? null : valoresDps.getValoresServicoPrestado();
        final NFSeSefinNacionalInfNFSeIBSCBSTotais totais = nota.ibsCbsNota() == null ? null : nota.ibsCbsNota().getTotCIBS();
        final BigDecimal ibs = totais == null || totais.getgIBS() == null ? null : totais.getgIBS().getvIBSTot();
        final BigDecimal cbs = totais == null || totais.getgCBS() == null ? null : totais.getgCBS().getvCBS();

        canvas.divisoria();
        canvas.tituloBloco("VALOR TOTAL DA NFS-e");
        canvas.campo(1, 1, "VALOR DA OPERAÇÃO / SERVIÇO", DanfseFormatador.valor(servico == null ? null : servico.getValorServicos()));
        canvas.campo(2, 1, "Desconto Incondicionado", DanfseFormatador.valor(descontoIncondicionado(nota)));
        canvas.campo(3, 1, "Desconto Condicionado", DanfseFormatador.valor(descontoCondicionado(nota)));
        canvas.avancar(LINHA_TOTAL);

        canvas.campo(0, 1, "Total das Retenções (ISSQN / Federais)", DanfseFormatador.valor(valores == null ? null : valores.getVTotalRet()));
        canvas.campo(1, 1, "VALOR LÍQUIDO DA NFS-e", DanfseFormatador.valor(valores == null ? null : valores.getVLiq()));
        final BigDecimal totalIbsCbs = ibs == null ? cbs : cbs == null ? ibs : ibs.add(cbs);
        canvas.campo(2, 1, "Total do IBS/CBS", DanfseFormatador.valor(totalIbsCbs));
        canvas.campo(3, 1, "VALOR LÍQUIDO DA NFS-e + IBS/CBS", DanfseFormatador.valor(totais == null ? null : totais.getvTotNF()));
        canvas.avancar(LINHA_TOTAL);
    }
}
