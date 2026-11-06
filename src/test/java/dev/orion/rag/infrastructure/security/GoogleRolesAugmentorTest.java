package dev.orion.rag.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleRolesAugmentorTest {

    @Test
    void acceptsDomainAndSubdomains() {
        assertTrue(GoogleRolesAugmentor.matchesDomain("prof@poa.ifrs.edu.br", "poa.ifrs.edu.br"));
        assertTrue(GoogleRolesAugmentor.matchesDomain("2023015625@aluno.poa.ifrs.edu.br", "poa.ifrs.edu.br"));
        assertTrue(GoogleRolesAugmentor.matchesDomain("x@aluno.poa.ifrs.edu.br", " @POA.ifrs.edu.br "));
    }

    @Test
    void rejectsOtherDomains() {
        assertFalse(GoogleRolesAugmentor.matchesDomain("alguem@gmail.com", "poa.ifrs.edu.br"));
        assertFalse(GoogleRolesAugmentor.matchesDomain("x@fakepoa.ifrs.edu.br", "poa.ifrs.edu.br"));
        assertFalse(GoogleRolesAugmentor.matchesDomain("x@poa.ifrs.edu.br.evil.com", "poa.ifrs.edu.br"));
    }
}
