package dev.orion.rag.domain.model;

public class TriagemResult {

    public enum Decisao {
        AUTO_RESPONDER, PEDIR_INFO, REDIRECIONAR, SOBRE_ASSISTENTE
    }

    public enum Urgencia {
        BAIXA, MEDIA, ALTA
    }

    /** Who a question should be forwarded to when the chatbot cannot answer it. */
    public enum Setor {
        /** Course coordination (Sistemas para Internet). */
        SSI,
        /** General campus contact. */
        GERAL
    }

    private Decisao decisao;
    private Urgencia urgencia;
    private String camposFaltantes; // comma-separated, nullable
    private Setor setor;

    public TriagemResult(Decisao decisao, Urgencia urgencia, String camposFaltantes) {
        this(decisao, urgencia, camposFaltantes, Setor.GERAL);
    }

    public TriagemResult(Decisao decisao, Urgencia urgencia, String camposFaltantes, Setor setor) {
        this.decisao = decisao;
        this.urgencia = urgencia;
        this.camposFaltantes = camposFaltantes;
        this.setor = setor == null ? Setor.GERAL : setor;
    }

    public Decisao getDecisao() { return decisao; }
    public Urgencia getUrgencia() { return urgencia; }
    public String getCamposFaltantes() { return camposFaltantes; }
    public Setor getSetor() { return setor; }
}
