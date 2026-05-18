package dev.orion.rag.domain.support;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class RagSourceFormatter {

    private RagSourceFormatter() {
    }

    private static final String PPC_URL =
            "https://poa.ifrs.edu.br/attachments/article/2808/PPC_SSI_POA_Final_Aprovado.docx.pdf";
    private static final String ANEXO_URL =
            "https://poa.ifrs.edu.br/attachments/article/2808/Res_2025_Anexo.pdf";
    private static final String ANEXO_69_URL =
            "https://poa.ifrs.edu.br/attachments/article/2808/69_ANEXO_DE_RESOLUCAO.pdf";
    private static final String DUVIDAS_RESPOSTAS_URL =
            "https://poa.ifrs.edu.br/index.php/duvidas-respostas";

    public static String sourcesAppendix(String ragContext) {
        if (ragContext == null || ragContext.isBlank()) {
            return "";
        }
        Set<String> sources = new LinkedHashSet<>();
        String[] lines = ragContext.split("\\R");
        for (String line : lines) {
            String s = line.trim();
            if (s.startsWith("[Fonte:") && s.endsWith("]")) {
                String name = s.substring("[Fonte:".length(), s.length() - 1).trim();
                if (!name.isBlank()) {
                    sources.add(name);
                }
            }
        }
        if (sources.isEmpty()) {
            return "\n\n---\nFontes consultadas: Base de conhecimento (RAG)";
        }
        StringBuilder sb = new StringBuilder("\n\n---\nFontes consultadas:");
        for (String src : sources) {
            sb.append("\n- ").append(formatSource(src));
        }
        return sb.toString();
    }

    private static String formatSource(String src) {
        String trimmed = src == null ? "" : src.trim();
        if (trimmed.isBlank()) {
            return "Base de conhecimento (RAG)";
        }

        // Already a URL: keep as-is.
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("ppc_ssi_poa_final_aprovado") || lower.contains("ppc_ssi_poa_final_aprovado.docx.pdf")) {
            return "[" + trimmed + "](" + PPC_URL + ")";
        }
        if (lower.contains("res_2025_anexo") || lower.contains("res_2025_anexo.pdf")) {
            return "[" + trimmed + "](" + ANEXO_URL + ")";
        }
        if (lower.contains("69_anexo_de_resolucao")) {
            return "[" + trimmed + "](" + ANEXO_69_URL + ")";
        }
        if (lower.contains("duvidas") && lower.contains("respostas")) {
            return "[Dúvidas e Respostas — IFRS POA](" + DUVIDAS_RESPOSTAS_URL + ")";
        }

        // Default: show filename only (clean, no raw RAG dump).
        return trimmed;
    }
}

