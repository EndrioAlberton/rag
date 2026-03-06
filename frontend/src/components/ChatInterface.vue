<template>
  <div class="chat-wrapper">
    <!-- Messages area -->
    <div class="messages-container" ref="chatContainer">
      <div v-if="error" class="mb-4">
        <v-alert type="error" dismissible @click:close="error = null">
          <div class="d-flex align-center">
            <span>{{ error }}</span>
            <v-spacer></v-spacer>
            <v-btn
              v-if="!conversationId"
              small
              color="error"
              text
              @click="initializeChat"
              class="ml-2"
            >
              Try Again
            </v-btn>
          </div>
        </v-alert>
      </div>
      <div v-if="initializing" class="text-center mt-4">
        <v-progress-circular indeterminate color="primary"></v-progress-circular>
        <div class="mt-2 text-body-2">Initializing conversation...</div>
      </div>
      <div v-else>
        <div 
          v-for="(message, index) in messages" 
          :key="index" 
          :class="['message-container', message.type === 'user' ? 'user-message' : 'assistant-message', { 'message-enter': message.isNew }]"
        >
          <v-card
            v-if="message.type === 'user'"
            class="user-message-bubble pa-3"
            style="max-width: 80%;"
          >
            <div class="text-body-1">
              {{ message.content }}
            </div>
          </v-card>
          <div 
            v-else 
            class="assistant-message-content markdown-content" 
            v-html="getRenderedMarkdown(message)"
          ></div>
        </div>
        <div v-if="isLoading" class="text-center mt-4">
          <v-progress-circular indeterminate color="primary" size="32"></v-progress-circular>
          <div class="mt-2 text-body-2 text--secondary">Processing...</div>
        </div>
      </div>
    </div>

    <!-- Message input - Always visible at the bottom -->
    <div class="input-container">
      <v-text-field
        v-model="prompt"
        label="Type your message..."
        outlined
        dense
        hide-details
        @keyup.enter="sendMessage"
        :disabled="isLoading || initializing || !conversationId"
        class="input-field"
      ></v-text-field>
      <v-btn
        color="primary"
        icon
        @click="sendMessage"
        :disabled="!prompt.trim() || isLoading || initializing || !conversationId"
        :loading="isLoading"
        class="send-button"
      >
        <v-icon>mdi-send</v-icon>
      </v-btn>
    </div>
  </div>
</template>

<script>
import { marked } from 'marked';
import hljs from 'highlight.js';
import 'highlight.js/styles/github-dark.css';
import { apiService } from '../services/api';
import { authService } from '../services/auth';

// Configure marked with appropriate options
marked.use({
  breaks: true, // Line breaks as <br>
  gfm: true, // GitHub Flavored Markdown
  highlight: function(code, lang) {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext';
    try {
      return hljs.highlight(code, { language }).value;
    } catch (err) {
      return hljs.highlight(code, { language: 'plaintext' }).value;
    }
  }
});

export default {
  name: 'ChatInterface',
  data() {
    return {
      prompt: '',
      messages: [],
      isLoading: false,
      initializing: true,
      error: null,
      conversationId: null,
      userId: null
    };
  },
  async mounted() {
    await this.initializeChat();
  },
  watch: {
    '$route.params.conversationId': {
      handler(newId, oldId) {
        // Always reinitialize when conversationId changes
        // This includes when changing from an ID to undefined (new conversation)
        if (newId !== oldId) {
          this.initializeChat();
        }
      },
      immediate: false
    },
    '$route.fullPath': {
      handler(newPath, oldPath) {
        // If navigating to /chat without conversationId, create new conversation
        // This ensures that even when already on /chat, a new navigation forces creation
        if (newPath === '/chat' && newPath !== oldPath) {
          this.initializeChat();
        }
      },
      immediate: false
    }
  },
  methods: {
    getRenderedMarkdown(message) {
      if (message.type !== 'assistant') return '';
      // Do not call cleanContent here, it will be called in renderMarkdown
      return this.renderMarkdown(message.content);
    },
    
    async initializeChat() {
      try {
        this.initializing = true;
        this.error = null;
        
        const user = authService.getUser();
        if (!user) {
          console.warn('User not authenticated, redirecting to login');
          this.$router.push('/login');
          return;
        }

        // Use hash as userId (backend syncs automatically via JWT)
        // Orion Users hash is used to map with the RAG system user
        this.userId = user.id || user.hash || user.email;
        
        if (!this.userId) {
          console.error('User without valid identifier:', user);
          this.error = 'Error: user without valid identifier. Please log in again.';
          this.initializing = false;
          setTimeout(() => {
            this.$router.push('/login');
          }, 2000);
          return;
        }
        
        console.log('Initializing chat for user:', this.userId);
        const routeConversationId = this.$route.params.conversationId;
        
        // Clear previous state when creating new conversation
        if (!routeConversationId || routeConversationId === 'undefined' || routeConversationId === 'null') {
          this.conversationId = null;
          this.messages = [];
        }
        
        // Verify if conversationId is valid (not undefined, null or empty string)
        if (routeConversationId && routeConversationId !== 'undefined' && routeConversationId !== 'null') {
          this.conversationId = routeConversationId;
          // Load message history
          await this.loadHistory();
        } else {
          // If no conversationId, create new conversation
          console.log('Creating new conversation for user:', this.userId);
          try {
            const conversation = await apiService.createConversation(this.userId, 'New Conversation');
            console.log('Conversation created:', conversation);
            
            if (conversation && conversation.id) {
              this.conversationId = conversation.id;
              // Usar replace para não adicionar ao histórico de navegação
              await this.$router.replace(`/chat/${this.conversationId}`);
            } else {
              throw new Error('Invalid response when creating conversation: no ID');
            }
          } catch (error) {
            console.error('Error creating conversation:', error);
            const errorMessage = error.message || error.response?.data?.message || 'Error creating conversation. Please try again.';
            this.error = errorMessage;
            this.initializing = false;
            // Redirect after showing error
            setTimeout(() => {
              this.$router.push('/conversations');
            }, 3000);
            return;
          }
        }

      } catch (error) {
        console.error('Error initializing chat:', error);
        this.error = error.message || 'Error initializing chat. Please reload the page.';
      } finally {
        this.initializing = false;
        this.$nextTick(() => {
          this.scrollToBottom();
        });
      }
    },

    cleanContent(text) {
      if (!text) return '';
      // Remove only "data:" prefixes that may appear at the start of lines
      // Preserve all other content, including markdown
      let cleaned = text.replace(/^data:\s*/gm, '');
      // Do not remove spaces or line breaks - marked needs them
      return cleaned;
    },

    renderMarkdown(text) {
      try {
        if (!text) return '';
        
        // Clean content first
        const cleaned = this.cleanContent(text);
        if (!cleaned) return '';
        
        // Normalize incomplete markdown during streaming
        const normalized = this.normalizeIncompleteMarkdown(cleaned);
        
        // Process line breaks before rendering
        // Ensure double line breaks create paragraphs
        const processed = this.processLineBreaks(normalized);
        
        // Render markdown - options already configured globally with marked.use()
        const html = marked.parse(processed);
        
        // Apply highlight.js after rendering
        this.$nextTick(() => {
          const elements = this.$el?.querySelectorAll('.markdown-content');
          if (elements) {
            elements.forEach(element => {
              element.querySelectorAll('pre code').forEach((block) => {
                if (!block.classList.contains('hljs')) {
                  hljs.highlightElement(block);
                }
              });
            });
          }
        });
        
        return html;
      } catch (error) {
        console.error('Error rendering markdown:', error);
        // On error, return escaped text
        return this.escapeHtml(text);
      }
    },

    processLineBreaks(text) {
      // Normalize different types of line breaks
      let processed = text
        .replace(/\r\n/g, '\n') // Normalize Windows line breaks
        .replace(/\r/g, '\n');   // Normalize Mac line breaks
      
      // If text has no line breaks (all on one line), add intelligent breaks
      const hasLineBreaks = processed.includes('\n');
      if (!hasLineBreaks || processed.split('\n').length < 3) {
        processed = this.addIntelligentLineBreaks(processed);
      }
      
      return processed;
    },

    addIntelligentLineBreaks(text) {
      let processed = text;
      
      // Patterns to add double line breaks (new paragraphs):
      // 1. Before important keywords (with or without colons)
      const keywords = [
        'History:', 'Context:', 'Question:', 'Answer:', 'Answers:',
        'Problems', 'Need', 'Architecture', 'Features', 'Benefits',
        'Example:', 'Examples:', 'Resources', 'Documentation', 'Tutorial', 'GitHub',
        'How it works:', 'What is:', 'Here are', "Let's go!", 'In summary',
        'Histórico:', 'Contexto:', 'Pergunta:', 'Resposta:', 'Respostas:',
        'Problemas', 'Necessidade', 'Arquitetura', 'Características', 'Benefícios',
        'Exemplo:', 'Exemplos:', 'Recursos', 'Documentação', 'Tutorial',
        'Como funciona:', 'O que é:', 'Aqui estão', 'Vamos lá!', 'Em resumo'
      ];
      keywords.forEach(keyword => {
        const escaped = this.escapeRegex(keyword);
        // Break before keyword if not at the start
        const regex = new RegExp(`([^\\n\\s])(${escaped})`, 'gi');
        processed = processed.replace(regex, '$1\n\n$2');
      });
      
      // 2. Before numbered lists (1., 2., 3., etc.) - more specific pattern
      processed = processed.replace(/([^\d])(\d+\.\s+[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ])/g, '$1\n\n$2');
      
      // 3. Before bullet lists (*, -, •) when followed by uppercase
      processed = processed.replace(/([^\n])([\*\-•]\s+[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ])/g, '$1\n$2');
      
      // 4. Before bold/markdown sections (**text**)
      processed = processed.replace(/([^\n])(\*\*[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ])/g, '$1\n\n$2');
      
      // 5. After periods followed by space and uppercase (new important sentences)
      // But avoid breaking on common abbreviations
      processed = processed.replace(/([.!?])\s+([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][a-záéíóúàèìòùâêîôûãõç]{3,})/g, '$1\n\n$2');
      
      // 6. Break after colons when followed by long text (definitions)
      processed = processed.replace(/(:[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][^.!?]{40,}?)([.!?]|$)/g, '$1$2\n\n');
      
      // 7. Break after exclamations followed by uppercase
      processed = processed.replace(/(!)\s+([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][a-záéíóúàèìòùâêîôûãõç]{2,})/g, '$1\n\n$2');
      
      // Clean consecutive line breaks (more than 2)
      processed = processed.replace(/\n{3,}/g, '\n\n');
      
      // Clean spaces at the start of lines
      processed = processed.replace(/\n\s+/g, '\n');
      
      // Clean line breaks at start and end
      processed = processed.trim();
      
      return processed;
    },

    escapeRegex(str) {
      return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    },

    normalizeIncompleteMarkdown(text) {
      // Only try to close incomplete code blocks during streaming
      // Do not modify other markdown aspects to preserve formatting
      let normalized = text;
      
      // Count backticks to check for incomplete code blocks
      const codeBlockMatches = normalized.match(/```/g);
      if (codeBlockMatches && codeBlockMatches.length % 2 !== 0) {
        // Incomplete code block - add temporary closing
        normalized += '\n```';
      }
      
      // Return text without other modifications to preserve markdown formatting
      return normalized;
    },

    escapeHtml(text) {
      const div = document.createElement('div');
      div.textContent = text;
      return div.innerHTML;
    },

    async loadHistory() {
      try {
        if (!this.conversationId) {
          return;
        }
        const memory = await apiService.getMemory(this.userId, this.conversationId);
        if (memory && memory.messages && Array.isArray(memory.messages)) {
          this.messages = memory.messages.map(msg => {
              // Map backend types (ASSISTANT, USER) to lowercase
            let type = 'assistant';
            if (msg.type) {
              const msgType = msg.type.toUpperCase();
              if (msgType === 'USER') {
                type = 'user';
              } else if (msgType === 'ASSISTANT') {
                type = 'assistant';
              }
            }
            return {
              type: type,
              content: msg.content || '',
              isNew: false
            };
          });
        }
      } catch (error) {
        console.error('Error loading history:', error);
        // Do not show fatal error, only log
        // User can continue chatting even without history
      }
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.chatContainer;
        if (container) {
          container.scrollTop = container.scrollHeight;
        }
      });
    },

    async sendMessage() {
      if (!this.prompt.trim() || this.isLoading || !this.conversationId) {
        if (!this.conversationId) {
          this.error = 'Conversation not initialized. Please reload the page.';
        }
        return;
      }

      const userMessage = this.prompt.trim();
      this.prompt = '';
      this.error = null;
      
      // Add user message
      const userMsgIndex = this.messages.length;
      this.messages.push({
        type: 'user',
        content: userMessage,
        isNew: true
      });

      // Remove isNew flag after animation
      this.$nextTick(() => {
        setTimeout(() => {
          if (this.messages[userMsgIndex] && this.messages[userMsgIndex].type === 'user') {
            this.$set(this.messages[userMsgIndex], 'isNew', false);
          }
        }, 300);
      });

      this.scrollToBottom();
      this.isLoading = true;

      // Add assistant message (empty initially)
      const botMessageIndex = this.messages.length;
      this.messages.push({
        type: 'assistant',
        content: '',
        isNew: true
      });

      try {
        await apiService.createChatbotStream(
          this.conversationId,
          userMessage,
          (data) => {
            // Update bot message incrementally
            if (this.messages[botMessageIndex]) {
              // Clean any "data:" that may appear
              const cleanedData = data.replace(/^data:\s*/gm, '').trim();
              if (cleanedData) {
                this.messages[botMessageIndex].content += cleanedData;
                // Auto-scroll while receiving data
                this.scrollToBottom();
              }
            }
          },
          (error) => {
            console.error('Stream error:', error);
            if (this.messages[botMessageIndex]) {
              this.messages[botMessageIndex].content = 'Error processing message. Please try again.';
            }
            this.error = error.message || 'Error processing message. Check your connection and try again.';
            this.isLoading = false;
          },
          () => {
            this.isLoading = false;
            // Remove isNew flag from assistant message after animation
            if (this.messages[botMessageIndex]) {
              this.$nextTick(() => {
                setTimeout(() => {
                  this.$set(this.messages[botMessageIndex], 'isNew', false);
                }, 300);
              });
            }
            this.scrollToBottom();
          }
        );
      } catch (error) {
        console.error('Error sending message:', error);
        if (this.messages[botMessageIndex]) {
          this.messages[botMessageIndex].content = 'Error sending message. Please try again.';
        }
        this.error = error.response?.data?.message || error.message || 'Error sending message. Check your connection and try again.';
        this.isLoading = false;
      }
    }
  }
};
</script>

<style scoped>
.chat-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  overflow: hidden;
}

.messages-container {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 1rem;
  min-height: 0;
}

.input-container {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem;
  border-top: 1px solid rgba(0, 0, 0, 0.12);
  background-color: white;
}

.input-field {
  flex: 1;
}

.send-button {
  flex-shrink: 0;
}

.message-container {
  margin-bottom: 1.5rem;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  margin-left: auto;
}

.assistant-message {
  display: flex;
  justify-content: center;
  width: 100%;
}

.user-message-bubble {
  background-color: #e0e0e0 !important;
  border-radius: 18px !important;
  color: #333 !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.assistant-message-content {
  text-align: justify;
  max-width: 80%;
  padding: 0.75rem 1rem;
  word-wrap: break-word;
  font-size: 0.9rem;
  line-height: 1.5;
  margin: 0 auto;
}

.markdown-content {
  word-wrap: break-word;
  color: #333;
}

/* Paragraphs */
.markdown-content :deep(p) {
  margin-bottom: 1rem;
  line-height: 1.6;
  min-height: 1.6em; /* Ensure minimum height for paragraphs */
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* Spacing between consecutive paragraphs */
.markdown-content :deep(p + p) {
  margin-top: 0.5rem;
}

/* Line breaks - ensure they are visible */
.markdown-content :deep(br) {
  line-height: 1.6;
}

/* Ensure spacing between paragraphs */
.markdown-content :deep(p + p) {
  margin-top: 0.75rem;
}

/* Headers */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 1.5rem;
  margin-bottom: 0.75rem;
  font-weight: 600;
  line-height: 1.25;
  color: #1a1a1a;
}

.markdown-content :deep(h1) {
  font-size: 1.75rem;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3rem;
}

.markdown-content :deep(h2) {
  font-size: 1.5rem;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3rem;
}

.markdown-content :deep(h3) {
  font-size: 1.25rem;
}

.markdown-content :deep(h4) {
  font-size: 1.1rem;
}

.markdown-content :deep(h5) {
  font-size: 1rem;
}

.markdown-content :deep(h6) {
  font-size: 0.9rem;
  color: #666;
}

.markdown-content :deep(h1:first-child),
.markdown-content :deep(h2:first-child),
.markdown-content :deep(h3:first-child) {
  margin-top: 0;
}

/* Inline code */
.markdown-content :deep(code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9em;
  color: #e83e8c;
}

/* Code blocks */
.markdown-content :deep(pre) {
  background-color: #1e1e1e;
  padding: 1rem;
  border-radius: 6px;
  overflow-x: auto;
  margin: 1rem 0;
  line-height: 1.45;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.markdown-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  color: #d4d4d4;
  font-size: 0.9em;
  display: block;
  overflow-x: auto;
}

/* Lists */
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.75rem 0;
  padding-left: 2rem;
  line-height: 1.6;
}

.markdown-content :deep(li) {
  margin: 0.25rem 0;
}

.markdown-content :deep(ul ul),
.markdown-content :deep(ol ol),
.markdown-content :deep(ul ol),
.markdown-content :deep(ol ul) {
  margin-top: 0.25rem;
  margin-bottom: 0.25rem;
}

/* Task lists */
.markdown-content :deep(input[type="checkbox"]) {
  margin-right: 0.5rem;
}

/* Blockquotes */
.markdown-content :deep(blockquote) {
  margin: 1rem 0;
  padding: 0.5rem 1rem;
  border-left: 4px solid #dfe2e5;
  background-color: rgba(0, 0, 0, 0.02);
  color: #6a737d;
  font-style: italic;
}

.markdown-content :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

/* Tables */
.markdown-content :deep(table) {
  border-collapse: collapse;
  margin: 1rem 0;
  width: 100%;
  display: block;
  overflow-x: auto;
}

.markdown-content :deep(thead) {
  background-color: rgba(0, 0, 0, 0.05);
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #dfe2e5;
  padding: 0.5rem 0.75rem;
  text-align: left;
}

.markdown-content :deep(th) {
  font-weight: 600;
  background-color: rgba(0, 0, 0, 0.05);
}

.markdown-content :deep(tr:nth-child(even)) {
  background-color: rgba(0, 0, 0, 0.02);
}

/* Links */
.markdown-content :deep(a) {
  color: #0366d6;
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(a:visited) {
  color: #6f42c1;
}

/* Images */
.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 1rem 0;
  display: block;
}

/* Horizontal rule */
.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid #eaecef;
  margin: 1.5rem 0;
}

/* Bold and italic text - use higher specificity */
.assistant-message-content.markdown-content :deep(strong),
.assistant-message-content.markdown-content :deep(b),
.markdown-content :deep(strong),
.markdown-content :deep(b) {
  font-weight: 700 !important;
  font-weight: bold !important;
  color: #1a1a1a !important;
  display: inline;
}

.markdown-content :deep(em),
.markdown-content :deep(i) {
  font-style: italic;
}

/* Strikethrough text */
.markdown-content :deep(del),
.markdown-content :deep(s) {
  text-decoration: line-through;
  opacity: 0.7;
}

/* Ensure content preserves formatting */
.markdown-content {
  word-wrap: break-word;
  overflow-wrap: break-word;
}

@keyframes messageEnter {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-enter {
  animation: messageEnter 0.3s ease-out;
}
</style>

