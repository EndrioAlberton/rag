# Avaliação antes e depois da conversão dos PDFs para Markdown

Objetivo: medir se converter os PDFs para Markdown (e mostrar trecho e página) melhora as respostas do chatbot.

- **Antes** (linha de base): 14/10/2026, base com os 9 PDFs ingeridos como texto puro (PDFBox). Preenchido abaixo.
- **Depois**: mesma base, mesmos PDFs convertidos para Markdown. Coluna em branco, a preencher.

## Como repetir

1. Fazer a conversão e reingerir a base (limpar `embeddings` e subir o app).
2. Rodar **as mesmas perguntas, na mesma ordem**, em conversas novas (uma pergunta por conversa, para a memória não interferir).
3. Extrair com a query:
   ```sql
   SELECT r.created_at, r.user_message, left(r.llm_response, 300) AS resposta,
          r.rag_score, r.llm_latency_ms, r.handoff_required
   FROM request_logs r ORDER BY r.created_at DESC LIMIT 20;
   ```
4. Preencher a coluna "Depois" com a nota: **✓** correta, **~** parcial/incompleta, **✗** errada, **?** não verificável.

Critérios: correção do conteúdo (conferido no PDF), recusa correta quando a base não tem a informação, fontes citadas relevantes, latência (RNF01: até 5 s).

## Linha de base (antes)

| # | Pergunta | Esperado (conferido no PDF) | Resposta do chatbot (resumo) | Antes | Depois |
|---|---|---|---|---|---|
| 1 | Qual o horário de funcionamento da biblioteca do campus? | Não consta na base: deve recusar | Recusou corretamente, mas listou PPC e Horários como fontes e `handoff_required = f` | ✓ | |
| 2 | Quais disciplinas obrigatórias não têm pré-requisito? | CPW I, Fundamentos, Inglês Técnico, IHC, Lógica, Intro. Extensão, Redes, S.O., Leitura e Prod. Textual, Metodologia, Empreendedorismo, Governança de TI, Informática e Sociedade, **Probabilidade e Estatística**, **Segurança e Auditoria** (e optativas) | Lista de 15, sem Probabilidade e Estatística nem Segurança e Auditoria; incluiu "Atividades Complementares" | ~ | |
| 3 | Quais os pré-requisitos de Programação para Dispositivos Móveis? | Programação para Web II | Programação para Web II | ✓ | |
| 4 | Em quais situações a matrícula pode ser cancelada? | Normas 2011, cap. XI | Lista longa (solicitação, não matrícula, conceito D/E por 3 períodos, prazo máximo, etc.) | ? | |
| 5 | Quantas horas de atividades complementares o curso exige? | 70 h (PPC) | 70 h; "podem ser feitas desde o 1º semestre" (confere) | ✓ | |
| 6 | Qual o tempo mínimo e o máximo para concluir o curso? | 3 anos mín.; 6 anos máx. (6 e 12 semestres) | 6 e 12 semestres; a "observação" (dobro menos um) é incoerente com 12 | ✓ | |
| 7 | Qual a carga horária total do curso? | 2.218 h (2.148 de componentes + 70 de AC; o TCC, 132 h, está dentro dos componentes) | 2.218 h, mas somou o TCC (132 h) como parcela à parte | ~ | |
| 8 | Qual a carga horária de Sistemas Operacionais? | 66 h (PPC: 66 = 39 + 27, colunas provavelmente teórica e prática) | 66 h; os números 39 e 27 vêm do PPC, mas rotulou como "presencial" e "a distância" (rótulo provavelmente trocado) | ~ | |
| 9 | Qual a bibliografia básica de Sistemas Operacionais? | Tanenbaum (2007), Oliveira et al. (2010), Ferreira (2008) | Confere | ✓ | |
| 10 | Em que aulas do cronograma de S.O. há avaliação? | Aulas 08 (09/09), 13 (21/10) e 19 (02/12), conferido visualmente no PDF | 09/09 (1ª), 21/10 (2ª) e 02/12 (avaliação). Confere | ✓ | |
| 11 | Qual conteúdo será trabalhado na aula de 11/11/2026 em S.O.? | Aula 16: Linux. Kernel (conferido visualmente no PDF) | "Linux. Kernel". Confere | ✓ | |
| 12 | Quais as datas dos exames finais da graduação em 2026/2? | 16 a 18 de dezembro | 16 a 18 de dezembro | ✓ | |
| 13 | O dia 08/08/2026 é sábado letivo? Substitui qual dia? | Sim, referente à segunda-feira 07/09 | Confere | ✓ | |
| 14 | Qual o período de ajuste de matrícula em 2026/2? | 03 a 05 de agosto | Confere | ✓ | |
| 15 | Qual o primeiro dia letivo de 2026/2 na graduação? | 03 de agosto | Confere | ✓ | |
| 16 | Qual o horário de Inglês Técnico do 1º semestre e quem é a professora? | Está na grade de horários (POA-SSI113); professora a conferir no PDF | Respondeu "não encontrei" | ? | |
| 17 | Em quais dias são as aulas de Lógica do 1º semestre e quem são os professores? | A conferir na grade de horários (a extração do PDF sai ilegível) | Seg. e qua., prof. Okuyama; acrescentou LPOO (não pedido) | ? | |
| 18 a 20 | (faltam as 3 perguntas restantes da rodada de 14/10) | | | | |

### Resumo da linha de base (17 de 20 respostas)

- **✓ 11, ~ 3, ✗ 0, ? 3.**
- **Latência:** média de 1,36 s, máxima de 2,93 s. Todas dentro do RNF01 (5 s).
- **Problemas concentrados em listas completas, somas e na grade de horários:** a lista de pré-requisitos (2), a soma da carga horária (7), o rótulo das colunas do PPC (8) e a grade de horários (16, 17). O cronograma de S.O. (10, 11) a extração em texto puro já acertou.
- **Encaminhamento ao humano subnotificado:** 2 recusas (1 e 16) e `handoff_required = 0` nas 17. O limiar de 0,55 não separa: as recusas tiveram `rag_score` 0,79, o mesmo patamar das respostas corretas (0,80 a 0,87).
- **Fontes citadas indevidas:** a recusa da biblioteca e respostas de S.O. citam documentos que não têm relação (PPC, plano de Informática e Sociedade, normas de 2011).

> **Correção (14/10):** a primeira versão desta tabela marcava 10 e 11 como erradas. A conferência tinha usado `pdftotext -layout`, que desalinha as linhas do cronograma. Depois de abrir a página do PDF como imagem, as duas respostas do chatbot estavam certas.

## Observações de 13/10 (base com 4 documentos, antes dos planos, calendário e horários)

| Pergunta | Resposta | Problema |
|---|---|---|
| Último dia para trancar disciplina no 2º semestre? | "Até a 4ª semana após o início das aulas" | Esperado 29/09/2026 (calendário, que ainda não estava na base) |
| Quem dá aula de Sistemas Operacionais? | "Vera Martins" | Plano de 2026.2 não estava na base; nome não confirmado |
| Receita de bolo / valor do auxílio estudantil | Recusou corretamente | `rag_score` 0,63 e 0,73 (acima do limiar), fontes listadas e handoff `f` |
