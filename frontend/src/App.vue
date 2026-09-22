<template>
  <v-app>
    <template v-if="isAuthenticated">
      <v-app-bar v-if="$vuetify.display.mobile" color="surface" flat density="comfortable">
        <v-app-bar-nav-icon @click="drawer = !drawer"></v-app-bar-nav-icon>
        <v-app-bar-title class="brand-title">Consulta AI</v-app-bar-title>
      </v-app-bar>

      <v-navigation-drawer
        v-model="drawer"
        :permanent="!$vuetify.display.mobile"
        width="260"
        color="surface"
      >
        <Sidebar @logout="logout" />
      </v-navigation-drawer>

      <v-main>
        <router-view></router-view>
      </v-main>
    </template>

    <template v-else>
      <v-main>
        <router-view></router-view>
      </v-main>
    </template>
  </v-app>
</template>

<script>
import { useAuthStore } from './stores/auth';
import Sidebar from './components/Sidebar.vue';

export default {
  name: 'App',
  components: { Sidebar },
  data() {
    return {
      drawer: true
    };
  },
  created() {
    this.drawer = !this.$vuetify.display.mobile;
  },
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

<style>
.brand-title {
  font-family: 'Fraunces', Georgia, serif;
  font-weight: 600;
}
.v-navigation-drawer {
  border-right: 1px solid var(--border) !important;
}
</style>
