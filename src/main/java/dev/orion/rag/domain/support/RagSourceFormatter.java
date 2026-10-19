package dev.orion.rag.domain.support;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RagSourceFormatter {

    private RagSourceFormatter() {
    }

    private static final String PPC_URL =
            "https://poa.ifrs.edu.br/attachments/article/2808/PPC_SSI_POA_Final_Aprovado.docx.pdf";
    private static final String ANEXO_URL =
            "https://poa.ifrs.edu.br/attachments/article/2808/Res_2025_Anexo.pdf";
    private static final String ANEXO_69_URL =
            "https://poa.ifrs.edu.br/attachments/article/2808/69_ANEXO_DE_RESOLUCAO.pdf";
    private static final String HORARIOS_2026_2_URL =
            "https://poa.ifrs.edu.br/attachments/article/100/Hor%C3%A1rios_Aulas_2026-2_VERSAO-FINAL_10_07_2026.pdf";
    private static final String ANEXO_24_2026_URL =
            "https://poa.ifrs.edu.br/attachments/article/3129/Anexo%20da%20Resolu%C3%A7%C3%A3o%2024_2026%20(07-08-2026).pdf";
    private static final String DUVIDAS_RESPOSTAS_URL =
            "https://poa.ifrs.edu.br/index.php/duvidas-respostas";

    /** Maximum length of the excerpt shown under each source. */
    static final int EXCERPT_MAX_CHARS = 200;

    public static String sourcesAppendix(String ragContext) {
        if (ragContext == null || ragContext.isBlank()) {
            return "";
        }
        // Source name -> text of its first (best-ranked) chunk; contexts arrive best match first.
        Map<String, StringBuilder> sources = new LinkedHashMap<>();
        StringBuilder current = null;
        for (String line : ragContext.split("\\R")) {
            String s = line.trim();
            if (s.startsWith("[Fonte:") && s.endsWith("]")) {
                String name = s.substring("[Fonte:".length(), s.length() - 1).trim();
                if (name.isBlank()) {
                    current = null;
                } else if (sources.containsKey(name)) {
                    current = null; // keep only the best-ranked chunk of each source
                } else {
                    current = new StringBuilder();
                    sources.put(name, current);
                }
            } else if (s.matches("-{3,}")) {
                current = null; // chunk separator: the excerpt ends here
            } else if (current != null && current.length() < EXCERPT_MAX_CHARS * 2) {
                current.append(line).append(' ');
            }
        }
        if (sources.isEmpty()) {
            return "\n\n---\nFontes consultadas: Base de conhecimento (RAG)";
        }
        StringBuilder sb = new StringBuilder("\n\n---\nFontes consultadas:");
        for (Map.Entry<String, StringBuilder> e : sources.entrySet()) {
            sb.append("\n- ").append(formatSource(e.getKey()));
            String excerpt = excerpt(e.getValue().toString());
            if (!excerpt.isEmpty()) {
                sb.append("\n  > Trecho: \"").append(excerpt).append('"');
            }
        }
        return sb.toString();
    }

    /**
     * Turns a raw Markdown chunk into a short plain-text excerpt: drops table pipes,
     * emphasis/heading markers, HTML line breaks and separators, then cuts at a word
     * boundary.
     */
    static String excerpt(String chunk) {
        String text = chunk
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("[|*_#>`]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= EXCERPT_MAX_CHARS) {
            return text;
        }
        int cut = text.lastIndexOf(' ', EXCERPT_MAX_CHARS);
        return text.substring(0, cut > 0 ? cut : EXCERPT_MAX_CHARS) + "…";
    }

    private static String formatSource(String src) {
        String trimmed = src == null ? "" : src.trim();
        // Base files are Markdown conversions of the original PDFs: show the original name.
        if (trimmed.endsWith(".md")) {
            trimmed = trimmed.substring(0, trimmed.length() - ".md".length()) + ".pdf";
        }
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
        if (lower.contains("horarios_aulas_2026-2")) {
            return "[" + trimmed + "](" + HORARIOS_2026_2_URL + ")";
        }
        if (lower.contains("anexo_resolucao_24_2026")) {
            return "[" + trimmed + "](" + ANEXO_24_2026_URL + ")";
        }
        if (lower.contains("duvidas") && lower.contains("respostas")) {
            return "[Dúvidas e Respostas — IFRS POA](" + DUVIDAS_RESPOSTAS_URL + ")";
        }

        // Default: show filename only (clean, no raw RAG dump).
        return trimmed;
    }
}

