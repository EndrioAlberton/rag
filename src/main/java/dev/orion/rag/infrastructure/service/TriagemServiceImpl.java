package dev.orion.rag.infrastructure.service;

import dev.orion.rag.domain.model.TriagemResult;
import dev.orion.rag.domain.model.TriagemResult.Decisao;
import dev.orion.rag.domain.model.TriagemResult.Urgencia;
import dev.orion.rag.domain.port.out.TriagemPort;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class TriagemServiceImpl implements TriagemPort {

    private static final String TRIAGEM_SYSTEM_PROMPT = """
Você é um triador de Service Desk para dúvidas institucionais do IFRS.
Avalie a mensagem atual JUNTO com o histórico da conversa fornecido.
Retorne SOMENTE um JSON com este formato exato:
{"decisao":"AUTO_RESPONDER","urgencia":"BAIXA","camposFaltantes":""}

Regras de decisão:
- AUTO_RESPONDER: perguntas sobre o IFRS, OU mensagens que são continuações ou refinamentos de uma pergunta anterior no histórico. Em caso de dúvida, prefira AUTO_RESPONDER.
- PEDIR_INFO: APENAS para mensagens completamente novas, sem relação com o histórico, onde a falta de informação torna impossível dar qualquer resposta (ex: "quero saber sobre inscrição" como primeira mensagem sem especificar curso). Nunca use PEDIR_INFO para follow-ups de uma conversa em andamento.
- REDIRECIONAR: claramente fora do escopo do IFRS.

Regras de urgência:
- BAIXA: perguntas gerais sem prazo imediato.
- MEDIA: dúvidas sobre processos em andamento.
- ALTA: prazo imediato ou situação urgente.

Retorne SOMENTE o JSON, sem explicações.
""";

    @RegisterAiService
    interface TriagemAI {
        @SystemMessage(TriagemServiceImpl.TRIAGEM_SYSTEM_PROMPT)
        @UserMessage("Histórico: {history}\n\nMensagem atual: {message}")
        String classify(String message, String history);
    }

    private final TriagemAI triagemAI;

    @Inject
    public TriagemServiceImpl(TriagemAI triagemAI) {
        this.triagemAI = triagemAI;
    }

    @Override
    public CompletionStage<TriagemResult> classify(String userMessage, String history) {
        String safeHistory = (history == null || history.isBlank()) ? "(sem histórico)" : history;
        return Uni.createFrom().item(() -> {
            try {
                String json = triagemAI.classify(userMessage, safeHistory);
                return parseTriagem(json);
            } catch (Exception e) {
                // Fallback: AUTO_RESPONDER BAIXA on any parse error
                return new TriagemResult(Decisao.AUTO_RESPONDER, Urgencia.BAIXA, null);
            }
        }).runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool())
          .subscribeAsCompletionStage();
    }

    private TriagemResult parseTriagem(String json) {
        // Simple JSON parse without extra deps
        Decisao decisao = Decisao.AUTO_RESPONDER;
        Urgencia urgencia = Urgencia.BAIXA;
        String campos = null;

        try {
            String d = extractJsonValue(json, "decisao");
            if (d != null) decisao = Decisao.valueOf(d.trim().toUpperCase());
        } catch (Exception ignored) {}

        try {
            String u = extractJsonValue(json, "urgencia");
            if (u != null) urgencia = Urgencia.valueOf(u.trim().toUpperCase());
        } catch (Exception ignored) {}

        try {
            String c = extractJsonValue(json, "camposFaltantes");
            if (c != null && !c.isBlank()) campos = c.trim();
        } catch (Exception ignored) {}

        return new TriagemResult(decisao, urgencia, campos);
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}
