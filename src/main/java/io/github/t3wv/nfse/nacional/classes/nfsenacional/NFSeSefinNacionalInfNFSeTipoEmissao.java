package io.github.t3wv.nfse.nacional.classes.nfsenacional;


import org.simpleframework.xml.Root;

@Root(name = "tpEmis")
public enum NFSeSefinNacionalInfNFSeTipoEmissao {

    NORMAL("1", "Emissão normal no modelo da NFS-e Nacional"),
    LEIAUTE_MUNICIPAL("2", "Emissão original em leiaute próprio do município com transcrição para o modelo da NFS-e Nacional");

    private final String codigo;
    private final String descricao;

    NFSeSefinNacionalInfNFSeTipoEmissao(final String codigo, final String descricao) {
        this.codigo = codigo;
        this.descricao = descricao;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public static NFSeSefinNacionalInfNFSeTipoEmissao valueOfCodigo(final String codigo) {
        for (final NFSeSefinNacionalInfNFSeTipoEmissao tipo : NFSeSefinNacionalInfNFSeTipoEmissao.values()) {
            if (tipo.getCodigo().equals(codigo)) {
                return tipo;
            }
        }
        return null;
    }
}