# Avaliação antes e depois da conversão dos PDFs para Markdown

Objetivo: medir se converter os PDFs para Markdown melhora as respostas do chatbot.

- **Antes** (linha de base): 14/10/2026 por volta das 19h20, base com os PDFs ingeridos como texto puro (PDFBox).
- **Depois**: 14/10/2026 entre 20h05 e 20h11, mesma base de 10 documentos, agora em Markdown (pymupdf4llm). Trechos de 800 tokens com sobreposição de 300, embeddings em lotes de 64.

Legenda: **✓** correta, **~** parcial/incompleta, **✗** errada, **?** não verificável. As respostas esperadas foram conferidas no PDF (o cronograma de S.O. foi conferido visualmente, abrindo a página).

## Resultado por pergunta

| # | Pergunta | Esperado | Antes | Depois | Observação do "Depois" |
|---|---|---|---|---|---|
| 1 | Horário de funcionamento da biblioteca | Não consta: deve recusar | ✓ | ✓ | Recusou. Citou PPC e Horários como fonte e `handoff_required = f` (igual ao antes) |
| 2 | Disciplinas obrigatórias sem pré-requisito | 15 obrigatórias + 2 optativas | ~ | ~ | Passou a incluir Probabilidade e Estatística e Segurança e Auditoria. Mas as duas execuções diferem: a 1ª está completa, a 2ª omitiu Empreendedorismo, Governança de TI e Informática e Sociedade. Em ambas entra "Atividades Complementares" (não consta na lista) |
| 3 | Pré-requisitos de Prog. para Dispositivos Móveis | Programação para Web II | ✓ | ✓ | Igual nas duas execuções |
| 4 | Situações de cancelamento da matrícula | Normas 2011, cap. XI | ? | ? | Lista longa e coerente. Latência de 4,1 s |
| 5 | Horas de atividades complementares | 70 h | ✓ | ✓ | Acrescentou "não valem atividades anteriores ao ingresso" (não conferido) |
| 6 | Tempo mínimo e máximo do curso | 6 e 12 semestres | ✓ | ✓ | A observação "dobro menos um" continua incoerente com 12 |
| 7 | Carga horária total do curso | 2.218 h (TCC 132 h está dentro dos 2.148) | ~ | ~ | Continua somando o TCC como parcela à parte |
| 8 | Carga horária de Sistemas Operacionais | 66 h | ~ | ✓ | Respondeu só "66 horas-relógio", sem o rótulo trocado de 39/27 |
| 9 | Bibliografia básica de S.O. | Tanenbaum, Oliveira, Ferreira | ✓ | ✓ | Igual |
| 10 | Aulas com avaliação em S.O. | 09/09, 21/10, 02/12 | ✓ | ✓ | Igual |
| 11 | Conteúdo da aula de 11/11 em S.O. | Linux. Kernel | ✓ | ✓ | Igual |
| 12 | Exames finais da graduação 2026/2 | 16 a 18 de dezembro | ✓ | ✓ | Igual |
| 13 | 08/08 é sábado letivo? Substitui qual dia? | Sim, segunda-feira 07/09 | ✓ | ✗ | **Regressão:** disse "sexta-feira, dia 07/08" |
| 14 | Ajuste de matrícula 2026/2 | 03 a 05 de agosto | ✓ | ✓ | Igual |
| 15 | Primeiro dia letivo 2026/2 | 03 de agosto | ✓ | ✓ | Igual |
| 16 | Horário de Inglês Técnico e professora | Está na grade de horários | ? | ? | Recusou ("não encontrei"). Provável falso negativo |
| 17 | Dias de Lógica e professores | Está na grade de horários | ? | ? | Antes respondeu algo (não verificável); agora recusa, e as fontes citadas nem incluem o PDF de horários |

## Resumo

| | Antes | Depois |
|---|---|---|
| ✓ | 11 | 11 |
| ~ | 3 | 2 |
| ✗ | 0 | 1 |
| ? | 3 | 3 |
| Latência média | 1,36 s | 1,42 s |
| Latência máxima | 2,93 s | 4,08 s |

- **A conversão para Markdown não trouxe ganho claro:** o saldo é 11 corretas nos dois cenários. Houve um ganho (#8), um provável ganho parcial (#2, com mais itens na lista) e uma regressão (#13).
- **Latência:** praticamente igual, todas as respostas dentro do RNF01 (5 s).
- **Os pontos fracos continuam:** a grade de horários (#16, #17) não é recuperada pela busca semântica, e a soma da carga horária (#7) segue errada.
- **Variabilidade entre execuções:** a mesma pergunta (#2) devolveu listas diferentes, com o mesmo contexto recuperado (`rag_score` idêntico, 0,8119). A diferença vem da geração do modelo. Para o artigo, vale registrar que cada resposta deve ser avaliada em mais de uma execução.
- **Limiar de similaridade não discrimina:** as duas recusas corretas (#1, #16) e a recusa de #17 tiveram `rag_score` entre 0,79 e 0,80, o mesmo patamar de respostas corretas (0,80 a 0,86). `handoff_required` continuou 0 em todas, porque só marca "sem contexto recuperado".
- **Fontes citadas sem relação:** a recusa da biblioteca cita o PPC e os Horários, e a carga horária total cita o plano de S.O.

## Pergunta extra (fora das 17)

| Pergunta | Resposta | Problema |
|---|---|---|
| "datas importantes de novembro" | Lista do calendário de novembro | Misturou itens de outubro: "12 - Nossa Senhora Aparecida" e "10 - Dia Nacional de Luta Contra a Violência à Mulher" são de outubro. O trecho do calendário atravessa a divisão dos meses. `rag_score` 0,69, latência 4,3 s |

## Conclusões para a próxima etapa

1. Converter o PDF inteiro para Markdown não basta para dados em grade (calendário mensal e grade de horários). Dá para testar uma divisão por mês no calendário e por turma na grade, para cada trecho ter um único mês ou uma única turma.
2. As regressões (#13, a mistura de novembro) nascem dos trechos que juntam linhas vizinhas. Reduzir o tamanho do trecho nesses dois documentos pode ajudar.
3. Marcar `handoff_required` também quando o modelo responde "não encontrei", e esconder as fontes nesse caso.

## Observações de 13/10 (base com 4 documentos, antes dos planos, calendário e horários)

| Pergunta | Resposta | Problema |
|---|---|---|
| Último dia para trancar disciplina no 2º semestre? | "Até a 4ª semana após o início das aulas" | Esperado 29/09/2026 (calendário, que ainda não estava na base) |
| Quem dá aula de Sistemas Operacionais? | "Vera Martins" | Plano de 2026.2 não estava na base; nome não confirmado |
| Receita de bolo / valor do auxílio estudantil | Recusou corretamente | `rag_score` 0,63 e 0,73 (acima do limiar), fontes listadas e handoff `f` |
