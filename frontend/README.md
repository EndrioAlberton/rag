# Frontend RAG Chatbot

Este é o frontend da aplicação RAG Chatbot, desenvolvido com Vue.js 3, Vuetify e Vite. O frontend fornece uma interface moderna e responsiva para interagir com o sistema RAG através de um chatbot conversacional.

## ⚡ Início Rápido

```bash
# 1. Instalar dependências
cd frontend
npm install

# 2. Configurar variáveis de ambiente
# Crie um arquivo .env na raiz do diretório frontend (veja seção abaixo)

# 3. Iniciar servidor de desenvolvimento
npm run dev

# Acesse http://localhost:5173
```

**⚠️ Importante:** Não esqueça de criar o arquivo `.env` com as variáveis necessárias, especialmente `VITE_GOOGLE_CLIENT_ID` se quiser usar o login com Google.

## 🛠️ Tecnologias Utilizadas

- **Vue.js 3** - Framework JavaScript progressivo para construção de interfaces
- **Vuetify 3** - Framework de componentes Material Design para Vue
- **Vite** - Build tool e servidor de desenvolvimento rápido
- **Vue Router** - Roteamento oficial para Vue.js
- **Pinia** - Store state management para Vue
- **Axios** - Cliente HTTP para requisições à API
- **Marked** - Parser de Markdown para renderização de mensagens
- **Highlight.js** - Destaque de sintaxe para blocos de código

## 📋 Pré-requisitos

- **Node.js** 18+ e npm
- Serviços backend em execução:
  - API RAG (padrão: `http://localhost:8081`)
  - Orion Users Service (padrão: `http://localhost:8080`)

## 🚀 Instalação e Configuração

### 1. Instalação de Dependências

```bash
cd frontend
npm install
```

### 2. Configuração das Variáveis de Ambiente

Crie um arquivo `.env` na raiz do diretório `frontend` com as seguintes variáveis:

```bash
# No diretório frontend/
touch .env
```

Conteúdo mínimo do arquivo `.env`:

```env
# URL base da API RAG
VITE_API_BASE_URL=http://localhost:8081

# URL do serviço Orion Users (autenticação)
VITE_ORION_USERS_URL=http://localhost:8080

# Google Client ID para autenticação social (obrigatório para login com Google)
VITE_GOOGLE_CLIENT_ID=seu-google-client-id-aqui
```

**⚠️ Importante:**
- Todas as variáveis devem começar com `VITE_` para serem expostas ao código frontend pelo Vite
- As variáveis de ambiente são **embutidas no código durante o build** - elas não são lidas em runtime
- Após criar ou modificar o arquivo `.env`, você **DEVE**:
  - Reiniciar o servidor de desenvolvimento (`npm run dev`) se estiver usando o Vite dev server
  - Fazer um novo build (`npm run build`) se estiver usando o frontend servido pelo Quarkus
- Não commite o arquivo `.env` no git (ele deve estar no `.gitignore`)
- Use valores diferentes para desenvolvimento, staging e produção

### 3. Configuração do Google Client ID

Para habilitar o login com Google, você precisa:

1. Acessar o [Google Cloud Console](https://console.cloud.google.com/)
2. Criar um novo projeto ou selecionar um existente
3. Ativar a API "Google Identity Services"
4. Criar credenciais OAuth 2.0:
   - Tipo: **ID de cliente OAuth 2.0**
   - Tipo de aplicativo: **Aplicativo da Web**
   - URIs de redirecionamento autorizados: `http://localhost:5173` (desenvolvimento)
   - Origens JavaScript autorizadas: `http://localhost:5173`
5. Copiar o **Client ID** gerado
6. Adicionar no arquivo `.env`:

```env
VITE_GOOGLE_CLIENT_ID=seu-client-id.apps.googleusercontent.com
```

## 🔧 Desenvolvimento

Para executar o frontend em modo de desenvolvimento:

```bash
cd frontend
npm install
npm run dev
```

O servidor de desenvolvimento estará disponível em `http://localhost:5173` com hot-reload habilitado.

### Comandos Disponíveis

```bash
# Modo de desenvolvimento
npm run dev

# Build para produção
npm run build

# Preview do build de produção
npm run preview
```

## 🏗️ Build de Produção

O build do frontend é gerado na pasta `../src/main/resources/META-INF/resources/` para ser servido diretamente pelo Quarkus:

```bash
cd frontend
npm run build
```

Após o build, os arquivos estáticos estarão disponíveis para o Quarkus servir.

**⚠️ Importante sobre Variáveis de Ambiente:**

As variáveis de ambiente do Vite (como `VITE_GOOGLE_CLIENT_ID`) são **embutidas no código durante o build**. Isso significa que:

1. **Você DEVE fazer o build ANTES de executar o Quarkus dev** se quiser usar a interface web servida pelo Quarkus
2. **As variáveis devem estar configuradas no `frontend/.env` ANTES do build**
3. **Se você modificar as variáveis de ambiente, será necessário fazer um novo build** para que as mudanças sejam refletidas
4. **No modo Quarkus dev**, o frontend é servido a partir dos arquivos buildados, não do servidor Vite

**Exemplo de fluxo:**

```bash
# 1. Configure as variáveis de ambiente
cd frontend
# Edite ou crie o arquivo .env com VITE_GOOGLE_CLIENT_ID, etc.

# 2. Faça o build
npm run build

# 3. Execute o Quarkus (em outro terminal ou após voltar para a raiz)
cd ..
./mvnw quarkus:dev
```

**Nota:** O diretório de saída é limpo automaticamente antes de cada build (`emptyOutDir: true`).

## ⚙️ Variáveis de Ambiente

### Variáveis Disponíveis

| Variável | Descrição | Padrão | Obrigatória |
|----------|-----------|--------|-------------|
| `VITE_API_BASE_URL` | URL base da API RAG backend | `http://localhost:8081` | Não |
| `VITE_ORION_USERS_URL` | URL do serviço Orion Users (autenticação) | `http://localhost:8080` | Não |
| `VITE_GOOGLE_CLIENT_ID` | Client ID do Google para autenticação social | - | Sim (para login Google) |

### Exemplo Completo de Arquivo `.env`

```env
# ============================================
# URLs dos Serviços Backend
# ============================================

# URL base da API RAG (serviço principal de chat e processamento)
# Padrão: http://localhost:8081
VITE_API_BASE_URL=http://localhost:8081

# URL do serviço Orion Users (autenticação e gerenciamento de usuários)
# Padrão: http://localhost:8080
VITE_ORION_USERS_URL=http://localhost:8080

# ============================================
# Autenticação Social (Google)
# ============================================

# Google Client ID para autenticação social
# OBRIGATÓRIO para habilitar login com Google
# Formato esperado: xxxxx-xxxxx.apps.googleusercontent.com
# 
# Como obter:
# 1. Acesse https://console.cloud.google.com/
# 2. Crie um projeto ou selecione um existente
# 3. Ative a API "Google Identity Services"
# 4. Crie credenciais OAuth 2.0:
#    - Tipo: ID de cliente OAuth 2.0
#    - Tipo de aplicativo: Aplicativo da Web
#    - URIs de redirecionamento: http://localhost:5173 (dev)
#    - Origens JavaScript: http://localhost:5173 (dev)
# 5. Copie o Client ID gerado e cole abaixo
#
# Deixe vazio se não desejar usar login com Google
VITE_GOOGLE_CLIENT_ID=123456789-abcdefghijklmnop.apps.googleusercontent.com
```

**Nota:** Para outros ambientes (staging/produção), ajuste os valores conforme necessário.

## 📁 Estrutura do Projeto

```
frontend/
├── src/
│   ├── components/          # Componentes Vue reutilizáveis
│   │   ├── ChatInterface.vue       # Interface principal do chat
│   │   ├── ConversationList.vue    # Lista de conversas
│   │   ├── ConversationItem.vue    # Item individual de conversa
│   │   ├── Login.vue               # Tela de login
│   │   ├── Register.vue            # Tela de registro
│   │   ├── Settings.vue            # Configurações do usuário
│   │   ├── TwoFactorAuth.vue       # Autenticação 2FA
│   │   └── TwoFactorSettings.vue   # Configurações 2FA
│   ├── services/           # Serviços de API e utilitários
│   │   ├── api.js                  # Cliente HTTP para API RAG
│   │   ├── auth.js                 # Gerenciamento de autenticação (localStorage)
│   │   └── orionUsers.js           # Serviço Orion Users (login, registro, 2FA)
│   ├── stores/             # Stores Pinia (gerenciamento de estado)
│   │   └── auth.js                 # Store de autenticação
│   ├── router/             # Configuração de rotas
│   │   └── index.js                # Definição de rotas e guards
│   ├── App.vue             # Componente raiz da aplicação
│   ├── main.js             # Ponto de entrada da aplicação
│   └── style.css           # Estilos globais
├── index.html              # Template HTML principal
├── vite.config.js          # Configuração do Vite
├── package.json            # Dependências e scripts
└── .env                    # Variáveis de ambiente (criar manualmente)
```

### Componentes Principais

#### ChatInterface.vue
Interface principal do chat que gerencia:
- Envio e recebimento de mensagens
- Streaming de respostas da API
- Renderização de markdown e código
- Gerenciamento de conversas

#### Login.vue / Register.vue
Autenticação de usuários com suporte a:
- Login tradicional (email/senha)
- Login social com Google (requer `VITE_GOOGLE_CLIENT_ID`)
- Autenticação de dois fatores (2FA)

#### ConversationList.vue
Lista e gerencia conversas:
- Visualização de conversas anteriores
- Criação de novas conversas
- Navegação entre conversas

## 🔐 Autenticação

O frontend suporta múltiplos métodos de autenticação:

### 1. Login Tradicional
- Email e senha
- Validação de senha (mínimo 8 caracteres, maiúscula, número, caractere especial)
- Suporte a autenticação de dois fatores (2FA)

### 2. Login Social (Google)
- Autenticação via Google Identity Services
- Requer configuração do `VITE_GOOGLE_CLIENT_ID`
- Integração com Google One Tap e botão customizado

### 3. Autenticação de Dois Fatores (2FA)
- Configurável por usuário
- Baseado em TOTP (Google Authenticator, Authy, etc.)
- Pode ser requerido para login básico e/ou login social

### Fluxo de Autenticação

```text
Usuário acessa /login
    ↓
Escolhe método: Tradicional ou Google
    ↓
Autentica no backend (Orion Users)
    ↓
Recebe JWT token
    ↓
Token armazenado (localStorage + Pinia store)
    ↓
Guards de rota verificam autenticação
    ↓
Acesso liberado para /conversations ou /chat
```

## 🌐 Rotas

As rotas principais da aplicação:

- `/login` - Tela de login (pública)
- `/register` - Tela de registro (pública)
- `/conversations` - Lista de conversas (protegida)
- `/chat` - Interface de chat (protegida)
- `/settings` - Configurações do usuário (protegida)

**Guards de Autenticação:** Rotas protegidas requerem autenticação válida. Usuários não autenticados são redirecionados para `/login`.

## 🔌 Integração com APIs

### API RAG (`/ai/*`)
Serviço principal de chat e processamento RAG:
- `POST /ai/chatbot` - Chat com streaming
- `GET /ai/memory` - Recuperar histórico
- `POST /ai/chat` - Enviar mensagem

### Orion Users (`/users/*`)
Serviço de autenticação e gerenciamento de usuários:
- `POST /users/create` - Criar usuário
- `POST /users/login` - Login tradicional
- `POST /users/login/google` - Login com Google
- `POST /users/login/2fa` - Login com 2FA
- `POST /users/2fa/settings` - Configurar 2FA

## 🐛 Troubleshooting

### Botão de Login Google Não Aparece

**Problema:** O botão "Entrar com Google" não está visível ou não funciona.

**Soluções:**
1. Verifique se o arquivo `.env` existe na raiz do diretório `frontend`
2. Confirme que `VITE_GOOGLE_CLIENT_ID` está configurado corretamente (não vazio)
3. **Se estiver usando o Quarkus dev mode:**
   - As variáveis de ambiente são embutidas no build
   - Você DEVE fazer `npm run build` no diretório `frontend` ANTES de executar o Quarkus
   - Se modificou o `.env`, faça um novo build
4. **Se estiver usando o Vite dev server (`npm run dev`):**
   - Reinicie o servidor de desenvolvimento após criar/modificar o `.env`
5. Verifique o console do navegador para erros de inicialização do Google Identity Services
6. Confirme que o script do Google está carregado no `index.html`:
   ```html
   <script src="https://accounts.google.com/gsi/client" async defer></script>
   ```

### Erro: "Google Client ID não configurado"

**Causa:** A variável `VITE_GOOGLE_CLIENT_ID` não está definida ou é uma string vazia.

**Solução:** Adicione a variável no arquivo `.env` e reinicie o servidor.

### Erro: "Google Identity Services não carregado"

**Causa:** O script do Google não foi carregado ou há problemas de conexão.

**Soluções:**
1. Verifique sua conexão com a internet
2. Confirme que não há bloqueadores de anúncio bloqueando o script
3. Verifique o console do navegador para erros de CORS ou carregamento

### Variáveis de Ambiente Não Funcionam

**Causa:** Variáveis não começam com `VITE_` ou o servidor não foi reiniciado.

**Soluções:**
1. Certifique-se que todas as variáveis começam com `VITE_`
2. Reinicie o servidor de desenvolvimento (`Ctrl+C` e `npm run dev`)
3. Limpe o cache do navegador se necessário

### Erro de CORS

**Causa:** O backend não está configurado para aceitar requisições do frontend.

**Solução:** Configure o CORS no backend para aceitar requisições de `http://localhost:5173`.

### Build Não Gera Arquivos

**Causa:** Erros de compilação ou problemas de permissão.

**Soluções:**
1. Verifique erros no terminal durante o build
2. Execute `npm install` novamente para garantir dependências corretas
3. Verifique permissões de escrita no diretório de saída

## 📚 Recursos Adicionais

- [Documentação Vue.js](https://vuejs.org/)
- [Documentação Vuetify](https://vuetifyjs.com/)
- [Documentação Vite](https://vitejs.dev/)
- [Google Identity Services](https://developers.google.com/identity/gsi/web)

## 🤝 Contribuindo

1. Faça um fork do projeto
2. Crie uma branch para sua feature (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -m 'Adiciona nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto contém informações confidenciais e proprietárias.
Cópia, distribuição ou uso não autorizado deste arquivo ou seu conteúdo é estritamente proibido.

© 2025 Rodrigo Prestes Machado. Todos os direitos reservados.

