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

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

@ApplicationScoped
public class TriagemServiceImpl implements TriagemPort {

    /** Matches combining diacritical marks left behind by NFD decomposition. */
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

    /**
     * Deterministic patterns for the most common ways students ask about the assistant
     * itself ("para que você serve", "o que você faz", "quem é você"...), checked before
     * the LLM triagem call. Matched against the message AFTER {@link #normalize}, so the
     * patterns below are written accent-free and lowercase on purpose — do not add
     * accented characters here.
     *
     * <p>In practice gpt-4o-mini does not reliably follow the {@code SOBRE_ASSISTENTE}
     * rule below for these phrasings — observed failure: "para oq vc serve" came back as
     * {@code PEDIR_INFO} with {@code camposFaltantes} echoing, almost verbatim, the
     * example text from the {@code PEDIR_INFO} rule ("instituição, curso ou contexto")
     * instead of reasoning about the actual message. A short-circuit here is immune to
     * that kind of classification noise and is also cheaper (no LLM call at all for a
     * hit). The LLM rule stays as a best-effort fallback for phrasings these patterns
     * don't cover.
     *
     * <p>Accepts common Brazilian Portuguese chat contractions ("vc", "oq") and "tu"
     * alongside "você" — colloquial in Rio Grande do Sul, where IFRS Porto Alegre is
     * located.
     */
    private static final List<Pattern> SOBRE_ASSISTENTE_PATTERNS = List.of(
            Pattern.compile("\\bpara\\s+((o\\s*)?que|oq)\\s+(voce|vc|tu)\\s+serve\\b"),
            Pattern.compile("\\b(o\\s*que|oq)\\s+(voce|vc|tu)\\s+(faz|e|sabe|pode|consegue)\\b"),
            Pattern.compile("\\bquem\\s+e\\s+(voce|vc|tu)\\b"),
            Pattern.compile("\\bquais\\s+.{0,25}(voce|vc|tu)\\s+(pode|consegue|sabe)\\b"),
            Pattern.compile("\\bcomo\\s+(voce|vc|tu)\\s+funciona\\b"),
            Pattern.compile("\\bem\\s+que\\s+(voce|vc|tu)\\s+(pode|consegue)\\s+(me\\s+)?ajudar\\b"));

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
  2. A mensagem é tão genérica que é impossível dar qualquer resposta útil (ex: "quero saber sobre inscrição" sem dizer se é matrícula, edital de ingresso ou inscrição em atividade complementar).
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
        if (matchesAboutAssistant(userMessage)) {
            return CompletableFuture.completedFuture(
                    new TriagemResult(Decisao.SOBRE_ASSISTENTE, Urgencia.BAIXA, null));
        }
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

    /**
     * Checks whether the message matches one of the deterministic self-referential
     * patterns, without calling the LLM.
     *
     * @param message the raw user message
     * @return true if the message asks about the assistant itself
     */
    private boolean matchesAboutAssistant(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalize(message);
        for (Pattern pattern : SOBRE_ASSISTENTE_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lowercases and strips diacritics (accents) so matching is immune to spelling
     * variants like "você"/"voce" or "é"/"e" — Brazilian Portuguese chat messages mix
     * both freely.
     *
     * @param message raw message
     * @return lowercase, accent-free version of the message
     */
    private static String normalize(String message) {
        String decomposed = Normalizer.normalize(message, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
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
