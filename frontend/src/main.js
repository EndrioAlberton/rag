import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { createVuetify } from 'vuetify';
import { createRouter, createWebHistory } from 'vue-router';
import 'vuetify/styles';
import '@mdi/font/css/materialdesignicons.css';
import './style.css';

import App from './App.vue';
import { useAuthStore } from './stores/auth';
import router from './router';

// Configurar Vuetify — identidade visual Consulta AI (fork do Orion)
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
const vuetify = createVuetify({
  theme: {
    defaultTheme: prefersDark ? 'dark' : 'light',
    themes: {
      light: {
        colors: {
          primary: '#00703C',
          'primary-darken-1': '#00512B',
          secondary: '#118A4C',
          error: '#D8322A',
          background: '#F5F8F5',
          surface: '#FFFFFF'
        }
      },
      dark: {
        colors: {
          primary: '#118A4C',
          'primary-darken-1': '#00351C',
          secondary: '#1E3A2A',
          error: '#D8322A',
          background: '#0C1712',
          surface: '#122019'
        }
      }
    }
  },
  defaults: {
    VCard: { rounded: 'lg' },
    VBtn: { rounded: 'lg' },
    VTextField: { variant: 'outlined', rounded: 'lg' }
  }
});

// Criar Pinia
const pinia = createPinia();

// Guard de autenticação
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore();
  
  // Verificar se a rota requer autenticação
  if (to.meta.requiresAuth) {
    // Verificar token no localStorage também (pode estar mais atualizado)
    const token = localStorage.getItem('jwt_token');
    const hasToken = !!token;
    
    // Se não há token e store não está autenticado, redirecionar para login
    if (!hasToken && !authStore.isAuthenticated) {
      console.log('Acesso negado: não autenticado, redirecionando para login');
      next('/login');
    } else {
      // Sincronizar store se necessário
      if (hasToken && !authStore.isAuthenticated) {
        authStore.setToken(token);
        const userData = localStorage.getItem('user_data');
        if (userData) {
          try {
            authStore.setUser(JSON.parse(userData));
          } catch (e) {
            console.error('Erro ao parsear user_data:', e);
          }
        }
      }
      next();
    }
  } else {
    // Rota pública, permitir acesso
    next();
  }
});

// Criar e montar aplicação
const app = createApp(App);
app.use(pinia);
app.use(router);
app.use(vuetify);

app.mount('#app');

