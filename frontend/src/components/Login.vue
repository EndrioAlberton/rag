<template>
  <AuthLayout>
    <h2 class="text-h5 mb-1" style="font-family:'Fraunces',Georgia,serif;">Bem-vindo(a)</h2>
    <p class="text-body-2 text-medium-emphasis mb-6">Entre com sua conta Google para tirar suas dúvidas sobre o curso.</p>

    <v-alert v-if="error" type="error" density="compact" class="mb-4">
      {{ error }}
    </v-alert>

    <div ref="googleBtn" class="d-flex justify-center"></div>
  </AuthLayout>
</template>

<script>
import { authService } from '../services/auth';
import { useAuthStore } from '../stores/auth';
import AuthLayout from './AuthLayout.vue';

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

// O ID token do Google é um JWT: o payload (2ª parte) é JSON em base64url
function decodeJwtPayload(token) {
  const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(b64), c => c.charCodeAt(0))));
}

export default {
  name: 'Login',
  components: { AuthLayout },
  data() {
    return { error: null };
  },
  mounted() {
    this.initGoogle();
  },
  methods: {
    initGoogle() {
      // O script do Google (index.html) carrega de forma assíncrona
      if (!window.google?.accounts?.id) {
        setTimeout(this.initGoogle, 200);
        return;
      }
      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: this.onCredential
      });
      window.google.accounts.id.renderButton(this.$refs.googleBtn, {
        theme: 'outline',
        size: 'large',
        text: 'signin_with',
        locale: 'pt-BR'
      });
    },

    async onCredential(response) {
      try {
        const token = response.credential;
        const payload = decodeJwtPayload(token);
        const userData = {
          id: payload.sub,
          hash: payload.sub,
          email: payload.email,
          name: payload.name,
          picture: payload.picture
        };

        authService.setToken(token);
        authService.setUser(userData);
        const authStore = useAuthStore();
        authStore.setToken(token);
        authStore.setUser(userData);

        await this.$nextTick();
        this.$router.push('/conversations');
      } catch (e) {
        console.error('Error signing in with Google:', e);
        this.error = 'Não foi possível entrar. Tente novamente.';
      }
    }
  }
};
</script>
