<template>
  <v-app>
    <v-app-bar color="primary" dark>
      <v-app-bar-title>Orion Chatbot</v-app-bar-title>
      <v-spacer></v-spacer>
      <v-btn v-if="isAuthenticated" to="/conversations" icon>
        <v-icon>mdi-message</v-icon>
      </v-btn>
      <v-btn v-if="isAuthenticated" to="/dashboard" icon>
        <v-icon>mdi-chart-bar</v-icon>
      </v-btn>
      <v-btn v-if="isAuthenticated" to="/settings" icon>
        <v-icon>mdi-cog</v-icon>
      </v-btn>
      <v-btn v-if="isAuthenticated" @click="logout" icon>
        <v-icon>mdi-logout</v-icon>
      </v-btn>
      <v-btn v-if="!isAuthenticated" to="/login" text>
        Login
      </v-btn>
      <v-btn v-if="!isAuthenticated" to="/register" text>
        Registrar
      </v-btn>
    </v-app-bar>
    <v-main class="fill-height" style="height: calc(100vh - 64px); overflow: hidden;">
      <router-view style="height: 100%;"></router-view>
    </v-main>
  </v-app>
</template>

<script>
import { useAuthStore } from './stores/auth';

export default {
  name: 'App',
  computed: {
    isAuthenticated() {
      const authStore = useAuthStore();
      return authStore.isAuthenticated;
    }
  },
  methods: {
    logout() {
      const authStore = useAuthStore();
      authStore.logout();
      this.$router.push('/login');
    }
  }
};
</script>

