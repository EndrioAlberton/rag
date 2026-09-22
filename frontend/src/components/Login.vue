<template>
  <AuthLayout>
    <h2 class="text-h5 mb-1" style="font-family:'Fraunces',Georgia,serif;">Bem-vindo(a) de volta</h2>
    <p class="text-body-2 text-medium-emphasis mb-6">Acesse para tirar suas dúvidas sobre o curso.</p>

    <v-form ref="form" v-model="valid" lazy-validation>
      <v-text-field
        v-model="email"
        :rules="emailRules"
        label="E-mail"
        required
        prepend-inner-icon="mdi-email-outline"
        type="email"
        class="mb-2"
      ></v-text-field>

      <v-text-field
        v-model="password"
        :rules="passwordRules"
        label="Senha"
        required
        prepend-inner-icon="mdi-lock-outline"
        :type="showPassword ? 'text' : 'password'"
        :append-inner-icon="showPassword ? 'mdi-eye' : 'mdi-eye-off'"
        @click:append-inner="showPassword = !showPassword"
        @keyup.enter="login"
      ></v-text-field>

      <v-alert v-if="error" type="error" density="compact" class="mb-2">
        {{ error }}
      </v-alert>

      <v-btn
        :disabled="!valid || loading"
        :loading="loading"
        color="primary"
        block
        size="large"
        class="mt-2"
        @click="login"
      >
        Entrar
      </v-btn>
    </v-form>

    <div class="text-center mt-4">
      <span class="text-body-2 text-medium-emphasis">Ainda não tem conta?</span>
      <v-btn variant="text" color="primary" to="/register" size="small">Criar conta</v-btn>
    </div>
  </AuthLayout>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';
import { authService } from '../services/auth';
import { useAuthStore } from '../stores/auth';
import AuthLayout from './AuthLayout.vue';

export default {
  name: 'Login',
  components: { AuthLayout },
  data() {
    return {
      valid: false,
      email: '',
      password: '',
      showPassword: false,
      loading: false,
      error: null,
      emailRules: [
        v => !!v || 'E-mail é obrigatório',
        v => /.+@.+\..+/.test(v) || 'Digite um e-mail válido'
      ],
      passwordRules: [
        v => !!v || 'Senha é obrigatória'
      ]
    };
  },
  methods: {
    async login() {
      if (!this.$refs.form.validate()) {
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        const response = await orionUsersService.login(this.email, this.password);

        if (response.authentication && response.authentication.token) {
          const token = response.authentication.token;
          const user = response.authentication.user;

          const userData = user ? {
            ...user,
            id: user.hash || user.email
          } : null;

          authService.setToken(token);
          if (userData) {
            authService.setUser(userData);
          }

          const authStore = useAuthStore();
          authStore.setToken(token);
          if (userData) {
            authStore.setUser(userData);
          }

          await this.$nextTick();

          this.$router.push('/conversations');
        } else {
          this.error = 'Não foi possível entrar. Tente novamente.';
        }
      } catch (error) {
        console.error('Error signing in:', error);
        this.error = error.response?.data?.message || 'Não foi possível entrar. Verifique suas credenciais.';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
