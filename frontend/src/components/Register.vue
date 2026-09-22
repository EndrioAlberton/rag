<template>
  <AuthLayout
    hero-title="Crie sua conta e tire suas dúvidas sobre o curso de SSI."
    hero-text="Leva menos de um minuto. Depois é só perguntar."
  >
    <h2 class="text-h5 mb-1" style="font-family:'Fraunces',Georgia,serif;">Criar conta</h2>
    <p class="text-body-2 text-medium-emphasis mb-6">Leva menos de um minuto.</p>

    <v-form ref="form" v-model="valid" lazy-validation>
      <v-text-field
        v-model="name"
        :rules="nameRules"
        label="Nome"
        required
        prepend-inner-icon="mdi-account-outline"
        class="mb-2"
      ></v-text-field>

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
        hint="Mínimo 8 caracteres, 1 maiúscula, 1 minúscula, 1 especial (!@#$...)"
        persistent-hint
        class="mb-2"
      ></v-text-field>

      <v-text-field
        v-model="confirmPassword"
        :rules="confirmPasswordRules"
        label="Confirmar senha"
        required
        prepend-inner-icon="mdi-lock-check-outline"
        :type="showPassword ? 'text' : 'password'"
        class="mb-2"
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
        @click="register"
      >
        Criar conta
      </v-btn>
    </v-form>

    <div class="text-center mt-4">
      <span class="text-body-2 text-medium-emphasis">Já tem uma conta?</span>
      <v-btn variant="text" color="primary" to="/login" size="small">Entrar</v-btn>
    </div>
  </AuthLayout>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';
import { authService } from '../services/auth';
import { useAuthStore } from '../stores/auth';
import AuthLayout from './AuthLayout.vue';

export default {
  name: 'Register',
  components: { AuthLayout },
  data() {
    return {
      valid: false,
      name: '',
      email: '',
      password: '',
      confirmPassword: '',
      showPassword: false,
      loading: false,
      error: null,
      nameRules: [
        v => !!v || 'Nome é obrigatório',
        v => (v && v.length >= 3) || 'Nome precisa ter pelo menos 3 caracteres'
      ],
      emailRules: [
        v => !!v || 'E-mail é obrigatório',
        v => /.+@.+\..+/.test(v) || 'Digite um e-mail válido'
      ],
      passwordRules: [
        v => !!v || 'Senha é obrigatória',
        v => !v || v.length >= 8 || 'Mínimo 8 caracteres',
        v => !v || /[A-Z]/.test(v) || 'Deve conter ao menos 1 maiúscula',
        v => !v || /[a-z]/.test(v) || 'Deve conter ao menos 1 minúscula',
        v => !v || /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(v) || 'Deve conter ao menos 1 caractere especial (!@#$...)'
      ],
      confirmPasswordRules: [
        v => !!v || 'Confirmação de senha é obrigatória',
        v => v === this.password || 'As senhas não coincidem'
      ]
    };
  },
  methods: {
    async register() {
      if (!this.$refs.form.validate()) {
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        const response = await orionUsersService.createAndAuthenticate(
          this.name,
          this.email,
          this.password
        );

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
          this.error = 'Não foi possível criar a conta. Tente novamente.';
        }
      } catch (error) {
        console.error('Error registering:', error);
        this.error = error.response?.data?.message || 'Não foi possível criar a conta. Tente novamente.';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
