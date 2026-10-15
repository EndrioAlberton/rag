# Histórico das alterações (13 e 14/10/2026)

Resumo do que foi feito no chatbot do TCC (Consulta AI, curso SSI do IFRS Campus Porto Alegre), por tema. Os hashes são dos commits na `main`.

## 1. Conversas e chat

| Mudança | Por quê | Commit |
|---|---|---|
| A conversa só é criada no primeiro envio. O título é um trecho (até 50 caracteres) da primeira mensagem | Abrir o chat ou clicar em "Nova conversa" criava uma conversa vazia no banco (e duas de uma vez, por clique duplo) | `51120ce` |
| Streaming: junta as linhas `data:` de um mesmo evento SSE | Durante a resposta, as quebras de linha se perdiam e a lista ficava colada. Ao reabrir a conversa, vinha correta do banco | `de37df7` |
| Streaming: cada token sai do servidor como string JSON, e o frontend decodifica | Reforço da correção anterior, independente de como o SSE trata linhas | `1ca8b7b` |

Observação: depois do deploy, o JavaScript antigo ficou em cache e mostrava as aspas e `\n` do JSON. Recarregar com Ctrl+Shift+R resolve.

## 2. Avaliação das respostas (RF07)

- Novo link **Reportar** ao lado de Curtir e Não curtir. Abre um modal para escrever um relato sobre a resposta.
- O relato é gravado em `request_log_feedback` com `value = REPORT` e o texto em `comment` (migration V16). Não entra nas contagens de curtir e não curtir. Commit `163e52f`.
- O dashboard passou a mostrar a pergunta e a resposta **completas** em painéis expansíveis (antes cortava em 300 caracteres). Commit `2de6404`.

## 3. Login com Google

- O login por e-mail e senha (Orion Users) foi trocado por **Entrar com Google**. O backend valida o ID token do Google (chaves públicas JWKS) e usa `sub` como identificador. Commit `e16cd5d`.
- O token do Google não traz papéis. `GoogleRolesAugmentor` dá `user` a todo e-mail verificado e `admin` aos e-mails de `RAG_ADMIN_EMAILS`. É possível restringir por domínio com `RAG_ALLOWED_EMAIL_DOMAIN`.
- Variáveis novas no `.env`: `GOOGLE_CLIENT_ID` (ID do cliente OAuth do tipo Web, criado no Google Cloud Console) e as duas acima.
- Correções posteriores: erro de compilação por variável usada em lambda (`5befd04`) e configs opcionais que derrubavam a subida quando vazias (`6261a2b`).
- Limitações: o token vale 1 hora, e o usuário é criado com o nome igual ao início do e-mail (dois e-mails com o mesmo início dão conflito).

## 4. Base de conhecimento

- Documentos adicionados: horários de aulas 2026-2, Anexo da Resolução 24/2026 (calendário), normas acadêmicas de 2011 e planos de ensino de Informática e Sociedade, Leitura e Produção Textual e Sistemas Operacionais (`5ff66f8`).
- O raspador de URLs (`rag.scrape.urls`) não trata PDF e hoje não tem efeito: os documentos entram pela pasta `src/main/resources/rag`.
- **Conversão para Markdown:** os 10 PDFs foram convertidos com pymupdf4llm. Os `.md` ficam em `src/main/resources/rag/` e os PDFs originais em `docs/fontes-originais/`, fora da ingestão (`f09f6cb`). "Fontes consultadas" continua mostrando o nome `.pdf` e o link original.
- **Embeddings em lotes de 64:** a OpenAI recusa requisições com mais de 300 mil tokens, e a ingestão de todos os documentos de uma vez estourava esse limite. Agora é um documento por vez, em lotes, apagando antes os trechos que ficaram de uma tentativa interrompida (`66a94cf`).
- Após trocar de PDF para Markdown é preciso limpar o banco antes de subir: `TRUNCATE embeddings, ingested_sources;`.

## 5. Encaminhamento ao atendimento humano

- Antes, `handoff_required` só era marcado quando a busca não recuperava nenhum trecho. Mas a busca quase sempre recupera algo (o limiar de similaridade é 0,55), e o modelo respondia "não encontrei" sem ser contabilizado.
- Agora, se a resposta do modelo começa com "Não encontrei essa informação", o atendimento é registrado como encaminhado com o motivo `model_not_found`. Em vez de "Fontes consultadas", a resposta mostra o e-mail e o telefone do suporte humano.
- O motivo `no_context` continua valendo quando nada é recuperado.
- O contato mostrado depende do **setor** da pergunta, escolhido pela triagem (o mesmo modelo que já classifica a urgência, sem chamada extra): perguntas do curso (disciplinas, pré-requisitos, planos de ensino, professores, TCC, estágio, atividades complementares, PPC) vão para a coordenação do SSI (Timóteo Alberto Peters Lange, `ssi@poa.ifrs.edu.br`, sala 816 da Torre Norte). O resto vai para o contato geral do campus (`comunicacao@poa.ifrs.edu.br`, (51) 3930-6002). Em caso de dúvida, usa o geral.
- Os contatos estão fixos em `ChatbotUseCase` (não em configuração). O setor ainda não é gravado no log.
- Arquivos: `ChatbotUseCase` e `AppendOnCompletePublisher`, com teste novo em `ChatbotUseCaseTest`. Ainda não commitado nem implantado.

## 6. Avaliação do desempenho

Detalhes em `avaliacao-antes-depois.md`. Resumo:

- Comparação de 17 perguntas, texto puro contra Markdown: 11 respostas corretas nos dois cenários. A conversão deu um ganho (carga horária de Sistemas Operacionais), um ganho parcial (lista de pré-requisitos) e uma regressão (sábado letivo de 08/08).
- Pontos fracos que seguem: a grade de horários não é recuperada, o calendário mistura meses e a carga horária total soma o TCC duas vezes.
- A mesma pergunta pode devolver respostas diferentes mesmo com o mesmo contexto. Cada resposta deve ser avaliada em mais de uma execução.
- O limiar de similaridade 0,55 não separa perguntas do curso de perguntas fora do escopo: as recusas tiveram `rag_score` de 0,79 a 0,80, o mesmo patamar das respostas corretas.
- Latência média de 1,4 s, dentro do RNF01 (até 5 s).

## 7. Artigo (repositório `tcc`)

- Texto ajustado conforme os comentários dos avaliadores e do orientador: tempos verbais, metodologia, usuários e SSI, autenticação, encaminhamento ao humano, quadros no lugar de tabelas, modelo de linguagem, consumo de tokens e resumo com 235 palavras. O registro antes e depois está em `ALTERACOES.md`.
- Pendente no artigo: a autenticação agora é Google (o texto cita Orion Users), o encaminhamento ao humano ganhou o motivo `model_not_found`, e faltam os dados de volume de demanda, a avaliação com usuários e trabalhos relacionados sobre atendimento.

## Pendências

- Mostrar o **trecho e a página** de onde a resposta saiu (RF03). Hoje só aparece o nome do arquivo.
- Dividir o calendário por mês e a grade de horários por turma, para melhorar a recuperação.
- Corrigir o PDF de Dúvidas e Respostas, que repete cada seção duas vezes.
- Tela para ler os relatos enviados em "Reportar".
- Conferir o limiar de similaridade (0,55).
