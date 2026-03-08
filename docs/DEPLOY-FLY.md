# Deploy no Fly.io (Quarkus JVM)

Este guia descreve como publicar a aplicação RAG no [Fly.io](https://fly.io) usando a imagem JVM (OpenJDK/Eclipse Temurin).

**Importante:** O `fly.toml` **não** provisiona Postgres nem Redis (no `fly launch` aparecem como "Postgres: &lt;none&gt;, Redis: &lt;none&gt;"). Isso é intencional: a aplicação **precisa** de Postgres (com PGVector) e Redis em produção — você deve criar/anexar instâncias no Fly.io (ou usar provedores externos) e configurar as URLs via `fly secrets set` (ver abaixo).

## Como ter Postgres e Redis no Fly.io

### Postgres (com PGVector)

O RAG usa a extensão **PGVector**. No Fly.io a opção recomendada é o **Managed Postgres (MPG)** com PGVector habilitado.

**1. Criar um cluster Managed Postgres com PGVector**
> ⚠️ **Aviso:** O Postgres “clássico”/unmanaged do Fly **não é recomendado** – não é gerenciado/suportado oficialmente, e você será responsável por operações, gerenciamento e recuperação de desastres.

Para uma solução gerenciada e suportada, crie um cluster **Managed Postgres (MPG)**:

```bash
fly mpg create
```

Mais informações: https://fly.io/docs/mpg/overview/

Siga as perguntas: nome do cluster, organização, região (ex.: `gru` para São Paulo), plano (ex.: `basic`). Anote o **ID do cluster** (ex.: `k1v53ol2l8n08q6p`) para os próximos passos.

Após o create, o CLI fica aguardando o cluster ficar pronto. Você pode:
- Acompanhar no dashboard: `https://fly.io/dashboard/<sua-org>/managed_postgres/<CLUSTER_ID>`
- Cancelar a espera com **Ctrl+C** — o provisionamento continua em segundo plano
- Quando estiver pronto, conectar ao banco com: `fly mpg connect --cluster <CLUSTER_ID>`

**2. Anexar o app RAG ao Postgres**

Quando o cluster estiver pronto:

```bash
fly mpg attach <CLUSTER_ID> -a <NOME_DO_SEU_APP_RAG>
```

Exemplo, se o app se chama `rag-weathered-leaf-9580` e o cluster ID é `k1v53ol2l8n08q6p`:

```bash
fly mpg attach k1v53ol2l8n08q6p -a rag-weathered-leaf-9580
```

Isso define o secret `DATABASE_URL` no app. Como o Quarkus espera `QUARKUS_DATASOURCE_JDBC_URL` e `QUARKUS_DATASOURCE_REACTIVE_URL`, é preciso derivar esses valores a partir do `DATABASE_URL` (veja a seção [Configurar secrets](#2-configurar-secrets) e [Conversão de DATABASE_URL](#conversão-de-database_url-fly-postgres)).

**Alternativa: Postgres padrão (sem MPG)**

Se preferir o Postgres “clássico” do Fly:

```bash
fly postgres create -n rag-db -r gru
fly postgres attach rag-db --app <NOME_DO_SEU_APP_RAG>
```

Isso também define `DATABASE_URL`. A extensão PGVector pode não vir habilitada; verifique a [documentação do Fly Postgres](https://fly.io/docs/postgres/) e, se necessário, use uma imagem que inclua PGVector ou habilite manualmente no banco.

### Redis (Upstash no Fly.io)

O Redis no Fly.io é oferecido via **Upstash** e gerenciado pelo CLI.

**1. Criar um banco Redis**

```bash
fly redis create
```

Escolha a organização, a região primária (de preferência a mesma do app, ex.: São Paulo) e, se quiser, regiões de réplica.

**2. Obter a URL de conexão**

```bash
fly redis list
fly redis status <NOME_DO_BANCO>
```

Na saída de `fly redis status` aparece o **Private URL** (ex.: `redis://...@fly-xxx.upstash.io`). Use esse valor no secret `QUARKUS_REDIS_HOSTS`:

```bash
fly secrets set QUARKUS_REDIS_HOSTS="redis://:SENHA@fly-NOME.upstash.io:PORTA"
```

(Substitua pela URL completa exibida em `fly redis status`.)

**3. Ver detalhes no console (opcional)**

```bash
fly redis dashboard
```

Abre o painel Upstash no navegador, onde você pode ver uso, conexões e a connection string.

---

Depois de criar e anexar Postgres e Redis, configure todos os secrets da aplicação conforme a seção [Configurar secrets](#2-configurar-secrets).

## Pré-requisitos

- Conta no [Fly.io](https://fly.io) e [flyctl](https://fly.io/docs/hands-on/install-flyctl/) instalado
- **PostgreSQL com extensão PGVector** — por exemplo [Fly Postgres](https://fly.io/docs/postgres/) ou outro provedor
- **Redis** — por exemplo [Upstash](https://upstash.com/) ou Redis na Fly
- **OPENAI_API_KEY** — chave da API OpenAI

## Primeiro deploy

Se ainda não tiver Postgres nem Redis no Fly.io, crie e anexe seguindo a seção [Como ter Postgres e Redis no Fly.io](#como-ter-postgres-e-redis-no-flyio) antes de configurar os secrets.

### 1. Criar o app (primeira vez)

Na raiz do projeto:

```bash
fly launch
```

- Escolha a região (ex.: `gru` para São Paulo).
- Se perguntar sobre Postgres/Redis, pode criar depois e configurar via secrets; não é obrigatório criar pelo `fly launch`.
- O `fly.toml` já está no projeto; o Fly usará o `Dockerfile` para build JVM.

Para só gerar/atualizar o `fly.toml` sem fazer deploy:

```bash
fly launch --no-deploy
```

### 2. Configurar secrets

A aplicação em produção usa o perfil `prod` e **não** inicia Dev Services (Postgres/Redis). Todas as conexões vêm de variáveis de ambiente. Defina os **secrets** (não coloque no `fly.toml`).

Se você usou `fly mpg attach` ou `fly postgres attach`, o Fly já definiu o secret `DATABASE_URL`. O Quarkus precisa ainda de `QUARKUS_DATASOURCE_JDBC_URL` e `QUARKUS_DATASOURCE_REACTIVE_URL` — derive-os do `DATABASE_URL` conforme a [conversão abaixo](#conversão-de-database_url-fly-postgres) (ou pegue a connection string no dashboard do MPG/Postgres e monte as duas URLs).

```bash
# Obrigatório: OpenAI
fly secrets set OPENAI_API_KEY=sk-...

# Obrigatório: PostgreSQL (JDBC e reactive)
# Se usar Fly Postgres: fly postgres connect -a <nome-do-app-postgres> e pegue a URL.
# Formato Fly: postgres://user:pass@hostname:5432/dbname
# Converta para:
# - JDBC:    jdbc:postgresql://hostname:5432/dbname
# - Reactive: postgresql://hostname:5432/dbname
fly secrets set QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://hostname:5432/dbname"
fly secrets set QUARKUS_DATASOURCE_REACTIVE_URL="postgresql://hostname:5432/dbname"

# Usuário e senha (se não estiverem na URL)
fly secrets set QUARKUS_DATASOURCE_USERNAME="usuario"
fly secrets set QUARKUS_DATASOURCE_PASSWORD="senha"

# Redis (ex.: Upstash ou redis://host:6379)
fly secrets set QUARKUS_REDIS_HOSTS="redis://default:senha@host:port"
```

**Conversão de `DATABASE_URL` (Fly Postgres)**  
O Fly Postgres fornece algo como:

`postgres://usuario:senha@nome-app-postgres.flycast:5432`

- **JDBC:**  
  `jdbc:postgresql://nome-app-postgres.flycast:5432/nome_do_banco`  
  (troque `postgres://` por `jdbc:postgresql://`, remova user:pass da URL e use `QUARKUS_DATASOURCE_USERNAME` e `QUARKUS_DATASOURCE_PASSWORD` se preferir)
- **Reactive:**  
  `postgresql://usuario:senha@nome-app-postgres.flycast:5432/nome_do_banco`

O nome do banco costuma ser `postgres` ou o que foi definido ao criar o Postgres.

### 3. Deploy

```bash
fly deploy
```

O build roda na Fly (Dockerfile multi-stage: frontend → JVM → runtime). A primeira vez pode levar alguns minutos (build Maven + empacotamento JVM).

## Build remoto e recursos

- O **build JVM** é feito nos builders da Fly (Maven + Eclipse Temurin); não é necessário GraalVM/Mandrel na sua máquina.
- Se o build falhar por **falta de memória (OOM)**, aumente o tamanho da VM no `fly.toml` (ex.: `memory = "2gb"` ou use um `size` maior). O build em si usa os recursos do builder; o `[[vm]]` no `fly.toml` afeta a **Machine** que roda a aplicação em produção.
- O **health check** está em `/q/health` (Quarkus). O `internal_port` é **8080**; não altere na app em prod (já configurado no perfil `prod`).

## Comandos úteis

```bash
fly status
fly logs
fly open          # Abre a URL do app no browser
fly ssh console   # Shell na Machine
fly secrets list  # Lista nomes dos secrets (valores não são exibidos)
```

## Resumo das variáveis em produção

| Variável | Obrigatório | Descrição |
|----------|-------------|-----------|
| `OPENAI_API_KEY` | Sim | Chave da API OpenAI |
| `QUARKUS_DATASOURCE_JDBC_URL` | Sim | URL JDBC do Postgres (jdbc:postgresql://...) |
| `QUARKUS_DATASOURCE_REACTIVE_URL` | Sim | URL reativa do Postgres (postgresql://...) |
| `QUARKUS_DATASOURCE_USERNAME` | Conforme necessidade | Usuário do banco |
| `QUARKUS_DATASOURCE_PASSWORD` | Conforme necessidade | Senha do banco |
| `QUARKUS_REDIS_HOSTS` | Sim | Endereço do Redis (ex.: redis://host:6379) |
| `RAG_LOCATION` | Não | Diretório dos documentos RAG na imagem (default: `/work/rag`) |

O `fly.toml` já define `RAG_LOCATION=/work/rag` em `[env]`. Os demais valores sensíveis devem ser configurados com `fly secrets set`.
