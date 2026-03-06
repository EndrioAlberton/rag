import axios from 'axios';

// Backend API Service
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Interceptor para adicionar token JWT
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('jwt_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Interceptor para tratar erros de autenticação
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Token inválido ou expirado
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user_data');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const apiService = {
  // Conversas
  async createConversation(userId, title) {
    try {
      const response = await api.post(`/ai/users/${userId}/conversations`, { title });
      if (!response.data || !response.data.id) {
        throw new Error('Resposta inválida do servidor: conversa criada sem ID');
      }
      return response.data;
    } catch (error) {
      console.error('Erro ao criar conversa:', error);
      // Re-throw com mensagem mais amigável
      if (error.response) {
        const message = error.response.data?.message || error.response.data?.error || 'Erro ao criar conversa';
        throw new Error(message);
      }
      throw error;
    }
  },

  async getUserConversations(userId) {
    const response = await api.get(`/ai/users/${userId}/conversations`);
    return response.data;
  },

  async getConversation(conversationId) {
    const response = await api.get(`/ai/conversations/${conversationId}`);
    return response.data;
  },

  async deleteConversation(conversationId, userId) {
    const response = await api.delete(`/ai/conversations/${conversationId}?userId=${userId}`);
    return response.data;
  },

  // Memória
  async getMemory(userId, conversationId) {
    try {
      const response = await api.get(`/ai/memory?userId=${userId}&conversationId=${conversationId}`);
      return response.data;
    } catch (error) {
      console.error('Erro ao carregar memória:', error);
      // Retornar null em caso de erro (conversa pode não ter histórico ainda)
      if (error.response && error.response.status === 404) {
        return null;
      }
      throw error;
    }
  },

  // Chatbot SSE (usando fetch com stream)
  async createChatbotStream(conversationId, prompt, onMessage, onError, onComplete) {
    const token = localStorage.getItem('jwt_token');
    if (!token) {
      onError(new Error('Token de autenticação não encontrado'));
      return;
    }

    const url = `${API_BASE_URL}/ai/chatbot`;
    
    let response;
    try {
      response = await fetch(url, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify({
          conversationId: conversationId,
          prompt: prompt
        })
      });
    } catch (error) {
      onError(new Error(`Erro de conexão: ${error.message}`));
      return;
    }

    if (!response.ok) {
      let errorMessage = `Erro HTTP ${response.status}`;
      try {
        const errorData = await response.json();
        errorMessage = errorData.message || errorMessage;
      } catch (e) {
        // If unable to parse JSON, use default message
        const text = await response.text();
        if (text) {
          errorMessage = text;
        }
      }
      onError(new Error(errorMessage));
      return;
    }

    if (!response.body) {
      onError(new Error('Resposta sem corpo'));
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        
        if (done) {
          // Processar buffer restante
          if (buffer.trim()) {
            const lines = buffer.split('\n');
            for (const line of lines) {
              const trimmedLine = line.trim();
              if (trimmedLine.startsWith('data: ')) {
                const data = trimmedLine.substring(6);
                if (data && data !== '[DONE]') {
                  onMessage(data);
                }
              } else if (trimmedLine && !trimmedLine.startsWith(':')) {
                // Se não começa com ':', pode ser conteúdo direto
                onMessage(trimmedLine);
              }
            }
          }
          onComplete();
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmedLine = line.trim();
          if (trimmedLine.startsWith('data: ')) {
            const data = trimmedLine.substring(6);
            if (data === '[DONE]') {
              onComplete();
              return;
            }
            if (data) {
              onMessage(data);
            }
          } else if (trimmedLine && !trimmedLine.startsWith(':') && !trimmedLine.startsWith('event:')) {
            // Aceitar conteúdo direto (alguns servidores SSE não usam prefixo 'data:')
            onMessage(trimmedLine);
          }
        }
      }
    } catch (error) {
      console.error('Erro ao processar stream:', error);
      onError(error);
    } finally {
      try {
        reader.releaseLock();
      } catch (e) {
        // Ignorar erro ao liberar lock
      }
    }
  }
};

