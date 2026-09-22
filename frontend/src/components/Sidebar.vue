<template>
  <div class="sidebar-inner">
    <div class="sidebar-mark">
      <v-icon size="28" color="primary">mdi-grid</v-icon>
      <div class="mark-text">
        <b>Consulta AI</b>
        <span>Atendimento SSI · IFRS</span>
      </div>
    </div>

    <v-list nav density="comfortable" class="sidebar-nav">
      <v-list-item to="/chat" prepend-icon="mdi-chat-outline" title="Assistente" rounded="lg"></v-list-item>
      <v-list-item to="/conversations" prepend-icon="mdi-message-text-outline" title="Minhas conversas" rounded="lg"></v-list-item>
      <v-list-item to="/dashboard" prepend-icon="mdi-chart-bar" title="Dashboard" rounded="lg"></v-list-item>
    </v-list>

    <div class="sidebar-user">
      <v-avatar color="primary" size="36">
        <span class="text-caption font-weight-bold" style="color:#fff">{{ initials }}</span>
      </v-avatar>
      <div class="who">
        <b>{{ userName }}</b>
        <span>{{ userEmail }}</span>
      </div>
      <v-btn icon="mdi-logout" variant="text" size="small" title="Sair" @click="$emit('logout')"></v-btn>
    </div>
  </div>
</template>

<script>
import { useAuthStore } from '../stores/auth';

export default {
  name: 'Sidebar',
  emits: ['logout'],
  computed: {
    userName() {
      const authStore = useAuthStore();
      return authStore.user?.name || authStore.user?.email || 'Usuário';
    },
    userEmail() {
      const authStore = useAuthStore();
      return authStore.user?.email || '';
    },
    initials() {
      return (this.userName || '?').trim().split(/\s+/).slice(0, 2).map(p => p[0]?.toUpperCase()).join('');
    }
  }
};
</script>

<style scoped>
.sidebar-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 12px;
}
.sidebar-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 24px;
}
.mark-text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}
.mark-text b {
  font-family: 'Fraunces', Georgia, serif;
  font-weight: 700;
  font-size: 1.02rem;
  color: var(--ink-900);
}
.mark-text span {
  font-size: 0.72rem;
  color: var(--ink-500);
}
.sidebar-nav {
  flex: 1;
}
.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
}
.who {
  flex: 1;
  min-width: 0;
}
.who b {
  display: block;
  font-size: 0.85rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.who span {
  font-size: 0.75rem;
  color: var(--ink-500);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
