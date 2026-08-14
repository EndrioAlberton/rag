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
- SOBRE_ASSISTENTE: use quando a pergunta é sobre o PRÓPRIO CHATBOT, não sobre o curso.
  Exemplos: "o que você faz", "para que você serve", "quais informações você pode me dar",
  "quais assuntos você cobre", "como você funciona", "quem é você", "o que você sabe",
  "em que você pode me ajudar". Essas perguntas NUNCA são PEDIR_INFO nem AUTO_RESPONDER —
  o sistema responde com uma descrição fixa do escopo do assistente, sem consultar a base.

- AUTO_RESPONDER: use em QUALQUER uma das situações abaixo:
  * O histórico não está vazio (já há conversa em andamento).
  * A mensagem menciona o IFRS, SSI, PPC, disciplina, semestre, TCC, estágio, frequência, reprovação, grade curricular ou atividades complementares.
  * A mensagem trata da vida acadêmica do estudante: abono de faltas, justificativa de faltas, atestado, matrícula, rematrícula, avaliação, segunda chamada, prazos, calendário, horários, notas, documentos ou coordenação.
  * A mensagem é uma continuação, refinamento ou pergunta de acompanhamento de qualquer tópico anterior.
  * Em caso de QUALQUER dúvida (que não seja sobre o assistente em si) → sempre prefira AUTO_RESPONDER.

- PEDIR_INFO: use SOMENTE quando TODAS as condições abaixo forem verdadeiras ao mesmo tempo:
  1. O histórico está completamente vazio (primeiro contato).
  2. A mensagem é tão genérica que é impossível dar qualquer resposta útil (ex: "quero saber sobre inscrição" sem mencionar instituição, curso ou contexto).
  3. Perguntar o dado faltante é estritamente necessário para responder.
  4. A mensagem NÃO cita nenhum tópico acadêmico concreto (se citar abono, falta, atestado, TCC, estágio, disciplina, matrícula, nota, prazo, etc., vá direto para AUTO_RESPONDER e deixe a base de conhecimento responder).
  5. A mensagem NÃO é uma pergunta sobre o próprio assistente (nesse caso é SOBRE_ASSISTENTE, nunca PEDIR_INFO).
  NUNCA use PEDIR_INFO se o histórico tiver qualquer mensagem anterior.
  NUNCA peça dados irrelevantes para uma dúvida institucional (ex: nome do professor, unidade, tipo de curso) — na dúvida, use AUTO_RESPONDER.

- REDIRECIONAR: use apenas quando a mensagem for claramente fora do escopo do IFRS (medicina, culinária, esportes, etc.).

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
