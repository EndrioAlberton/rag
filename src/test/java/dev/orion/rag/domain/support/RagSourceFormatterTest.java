package dev.orion.rag.domain.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagSourceFormatterTest {

    @Test
    void showsExcerptOfBestChunkPerSource() {
        String context = "[Fonte: PPC_SSI_POA_Final_Aprovado.docx.md]\n"
                + "## Estágio\n**O estágio** não é obrigatório.\n"
                + "\n\n---\n\n"
                + "[Fonte: PPC_SSI_POA_Final_Aprovado.docx.md]\nTrecho menos relevante do mesmo documento."
                + "\n\n---\n\n"
                + "[Fonte: Horarios_Aulas_2026-2_VERSAO-FINAL_10_07_2026.md]\n|Segunda<br>19:00|Rodrigo Machado|";

        String out = RagSourceFormatter.sourcesAppendix(context);

        assertTrue(out.contains("> Trecho: \"Estágio O estágio não é obrigatório.\""), out);
        assertFalse(out.contains("menos relevante"), out);
        assertTrue(out.contains("> Trecho: \"Segunda 19:00 Rodrigo Machado\""), out);
        assertFalse(out.contains("---\" "), out);
    }

    @Test
    void cutsLongExcerptAtWordBoundary() {
        String excerpt = RagSourceFormatter.excerpt("palavra ".repeat(60));

        assertTrue(excerpt.endsWith("palavra…"), excerpt);
        assertTrue(excerpt.length() <= RagSourceFormatter.EXCERPT_MAX_CHARS + 1, excerpt);
    }

    @Test
    void keepsGenericLabelWhenNoSourceIsTagged() {
        assertEquals("\n\n---\nFontes consultadas: Base de conhecimento (RAG)",
                RagSourceFormatter.sourcesAppendix("texto sem marcação de fonte"));
    }
}
