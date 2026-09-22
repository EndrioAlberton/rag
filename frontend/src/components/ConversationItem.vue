<template>
  <div class="conv-card" @click="$emit('select', conversation.id)">
    <v-icon size="20" color="primary" class="conv-icon">mdi-message-text-outline</v-icon>
    <div class="conv-body">
      <div class="conv-title">{{ conversation.title }}</div>
      <div class="conv-meta">
        Criada em {{ formatDate(conversation.createdAt) }}
        <span v-if="conversation.lastActivity"> · Última atividade em {{ formatDate(conversation.lastActivity) }}</span>
      </div>
    </div>
    <v-menu @click.stop>
      <template v-slot:activator="{ props }">
        <v-btn icon variant="text" size="small" v-bind="props" @click.stop>
          <v-icon>mdi-dots-vertical</v-icon>
        </v-btn>
      </template>
      <v-list>
        <v-list-item @click="$emit('select', conversation.id)">
          <v-list-item-title>Abrir</v-list-item-title>
        </v-list-item>
        <v-list-item @click="openRename">
          <v-list-item-title>Renomear</v-list-item-title>
        </v-list-item>
        <v-list-item @click="confirmDelete">
          <v-list-item-title>Excluir</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>

  <v-dialog v-model="renameDialog" max-width="480">
    <v-card>
      <v-card-title>Renomear conversa</v-card-title>
      <v-card-text>
        <v-text-field
          v-model="newTitle"
          label="Título"
          variant="outlined"
          density="comfortable"
          hide-details="auto"
          :disabled="renaming"
          @keyup.enter="submitRename"
        />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="renameDialog = false" :disabled="renaming">Cancelar</v-btn>
        <v-btn color="primary" :loading="renaming" @click="submitRename">Salvar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import { apiService } from '../services/api';

export default {
  name: 'ConversationItem',
  props: {
    conversation: {
      type: Object,
      required: true
    }
  },
  emits: ['select', 'delete', 'renamed'],
  data() {
    return {
      renameDialog: false,
      newTitle: '',
      renaming: false
    };
  },
  methods: {
    formatDate(dateString) {
      if (!dateString) return '';
      const date = new Date(dateString);
      return date.toLocaleDateString('pt-BR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    },

    openRename() {
      this.newTitle = this.conversation.title || '';
      this.renameDialog = true;
    },

    async submitRename() {
      const title = (this.newTitle || '').trim();
      if (!title) {
        return;
      }
      this.renaming = true;
      try {
        await apiService.updateConversationTitle(this.conversation.id, title);
        this.renameDialog = false;
        this.$emit('renamed');
      } catch (e) {
        console.error('Error renaming conversation:', e);
        alert(e.response?.data?.message || e.message || 'Não foi possível renomear a conversa');
      } finally {
        this.renaming = false;
      }
    },

    confirmDelete() {
      if (confirm('Tem certeza que deseja excluir esta conversa?')) {
        this.$emit('delete', this.conversation.id);
      }
    }
  }
};
</script>

<style scoped>
.conv-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.conv-card:hover {
  border-color: var(--verde-600);
  box-shadow: 0 1px 2px rgba(14, 31, 22, 0.06), 0 8px 24px rgba(14, 31, 22, 0.06);
}
.conv-icon {
  flex-shrink: 0;
}
.conv-body {
  flex: 1;
  min-width: 0;
}
.conv-title {
  font-weight: 600;
  font-size: 0.94rem;
  color: var(--ink-900);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-meta {
  font-size: 0.78rem;
  color: var(--ink-500);
  margin-top: 2px;
}
</style>
