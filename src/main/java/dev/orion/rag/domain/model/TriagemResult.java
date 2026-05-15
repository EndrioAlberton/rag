package dev.orion.rag.domain.model;

public class TriagemResult {

    public enum Decisao {
        AUTO_RESPONDER, PEDIR_INFO, REDIRECIONAR
    }

    public enum Urgencia {
        BAIXA, MEDIA, ALTA
    }

    private Decisao decisao;
    private Urgencia urgencia;
    private String camposFaltantes; // comma-separated, nullable

    public TriagemResult(Decisao decisao, Urgencia urgencia, String camposFaltantes) {
        this.decisao = decisao;
        this.urgencia = urgencia;
        this.camposFaltantes = camposFaltantes;
    }

    public Decisao getDecisao() { return decisao; }
    public Urgencia getUrgencia() { return urgencia; }
    public String getCamposFaltantes() { return camposFaltantes; }
}
