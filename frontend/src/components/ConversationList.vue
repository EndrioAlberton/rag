<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <v-card>
          <v-card-title class="d-flex align-center">
            <span>Minhas conversas</span>
            <v-spacer></v-spacer>
            <v-btn
              type="button"
              color="primary"
              @click.stop.prevent="createNewConversation"
              :loading="creatingConversation"
              :disabled="creatingConversation"
            >
              <v-icon left>mdi-plus</v-icon>
              Nova conversa
            </v-btn>
          </v-card-title>
          <v-card-text>
            <v-text-field
              v-model="search"
              label="Buscar conversas"
              prepend-inner-icon="mdi-magnify"
              clearable
              class="mb-4"
            ></v-text-field>

            <div v-if="conversations.length > 0" class="conv-list">
              <ConversationItem
                v-for="conversation in filteredConversations"
                :key="conversation.id"
                :conversation="conversation"
                @delete="handleDelete"
                @select="handleSelect"
                @renamed="loadConversations"
              />
            </div>

            <v-alert v-else-if="!loading" type="info">
              Você ainda não tem conversas. Crie uma nova conversa para começar!
            </v-alert>

            <div v-if="loading" class="text-center mt-4">
              <v-progress-circular indeterminate color="primary"></v-progress-circular>
            </div>
          </v-card-text>
        </v-card>
        
        <!-- Snackbar for errors -->
        <v-snackbar
          v-model="showError"
          color="error"
          :timeout="5000"
          top
        >
          {{ errorMessage }}
          <template v-slot:action="{ attrs }">
            <v-btn
              text
              v-bind="attrs"
              @click="showError = false"
            >
              Fechar
            </v-btn>
          </template>
        </v-snackbar>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { apiService } from '../services/api';
import { authService } from '../services/auth';
import ConversationItem from './ConversationItem.vue';

export default {
  name: 'ConversationList',
  components: {
    ConversationItem
  },
  data() {
    return {
      conversations: [],
      loading: false,
      search: '',
      creatingConversation: false,
      showError: false,
      errorMessage: ''
    };
  },
  computed: {
    filteredConversations() {
      if (!this.search) {
        return this.conversations;
      }
      const searchLower = this.search.toLowerCase();
      return this.conversations.filter(conv =>
        conv.title.toLowerCase().includes(searchLower)
      );
    }
  },
  async mounted() {
    await this.loadConversations();
  },
  methods: {
    async loadConversations() {
      const user = authService.getUser();
      if (!user || !user.id) {
        this.$router.push('/login');
        return;
      }

      this.loading = true;
      try {
        this.conversations = await apiService.getUserConversations(user.id);
      } catch (error) {
        console.error('Error loading conversations:', error);
      } finally {
        this.loading = false;
      }
    },

    async createNewConversation(event) {
      // Prevent default behavior (navigation, submit, etc)
      if (event) {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
      }

      const user = authService.getUser();
      if (!user || !user.id) {
        this.$router.push('/login');
        return;
      }

      this.creatingConversation = true;
      this.showError = false;
      this.errorMessage = '';

      try {
        console.log('Creating new conversation for user:', user.id);
        // Create conversation in database before navigating
        const conversation = await apiService.createConversation(user.id, 'Nova conversa');
        console.log('Conversation created successfully:', conversation);
        console.log('Response type:', typeof conversation);
        console.log('Conversation ID:', conversation?.id);
        
        // Validate response
        if (!conversation) {
          throw new Error('Resposta vazia do servidor');
        }

        if (!conversation.id) {
          console.error('Response without ID:', conversation);
          throw new Error('Resposta inválida do servidor: conversa criada sem ID');
        }
        
        // Navigate to chat screen with the created conversation ID
        const chatRoute = `/chat/${conversation.id}`;
        console.log('Navigating to:', chatRoute);
        await this.$router.push(chatRoute);
        console.log('Navigation completed');
      } catch (error) {
        console.error('Error creating new conversation:', error);
        console.error('Stack trace:', error.stack);
        const errorMsg = error.message || error.response?.data?.message || 'Erro ao criar conversa. Tente novamente.';
        this.errorMessage = errorMsg;
        this.showError = true;
        // Do not navigate on error - leave user on conversations page
      } finally {
        this.creatingConversation = false;
      }
    },

    handleSelect(conversationId) {
      this.$router.push(`/chat/${conversationId}`);
    },

    async handleDelete(conversationId) {
      const user = authService.getUser();
      if (!user || !user.id) {
        return;
      }

      try {
        await apiService.deleteConversation(conversationId, user.id);
        await this.loadConversations();
      } catch (error) {
        console.error('Error deleting conversation:', error);
      }
    }
  }
};
</script>

<style scoped>
.conv-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>

