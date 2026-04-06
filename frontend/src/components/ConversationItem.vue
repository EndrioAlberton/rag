<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title>{{ conversation.title }}</v-list-item-title>
      <v-list-item-subtitle>
        Created at: {{ formatDate(conversation.createdAt) }}
        <span v-if="conversation.lastActivity">
          | Last activity: {{ formatDate(conversation.lastActivity) }}
        </span>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-menu>
        <template v-slot:activator="{ props }">
          <v-btn icon v-bind="props">
            <v-icon>mdi-dots-vertical</v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item @click="$emit('select', conversation.id)">
            <v-list-item-title>Open</v-list-item-title>
          </v-list-item>
          <v-list-item @click="openRename">
            <v-list-item-title>Rename</v-list-item-title>
          </v-list-item>
          <v-list-item @click="confirmDelete">
            <v-list-item-title>Delete</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-list-item-action>
  </v-list-item>
  <v-dialog v-model="renameDialog" max-width="480">
    <v-card>
      <v-card-title>Rename conversation</v-card-title>
      <v-card-text>
        <v-text-field
          v-model="newTitle"
          label="Title"
          variant="outlined"
          density="comfortable"
          hide-details="auto"
          :disabled="renaming"
          @keyup.enter="submitRename"
        />
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="renameDialog = false" :disabled="renaming">Cancel</v-btn>
        <v-btn color="primary" :loading="renaming" @click="submitRename">Save</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
  <v-divider></v-divider>
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
      return date.toLocaleDateString('en-US', {
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
        alert(e.response?.data?.message || e.message || 'Could not rename conversation');
      } finally {
        this.renaming = false;
      }
    },

    confirmDelete() {
      if (confirm('Are you sure you want to delete this conversation?')) {
        this.$emit('delete', this.conversation.id);
      }
    }
  }
};
</script>

