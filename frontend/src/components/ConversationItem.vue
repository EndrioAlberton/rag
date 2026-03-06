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
          <v-list-item @click="confirmDelete">
            <v-list-item-title>Delete</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-list-item-action>
  </v-list-item>
  <v-divider></v-divider>
</template>

<script>
export default {
  name: 'ConversationItem',
  props: {
    conversation: {
      type: Object,
      required: true
    }
  },
  emits: ['select', 'delete'],
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

    confirmDelete() {
      if (confirm('Are you sure you want to delete this conversation?')) {
        this.$emit('delete', this.conversation.id);
      }
    }
  }
};
</script>

