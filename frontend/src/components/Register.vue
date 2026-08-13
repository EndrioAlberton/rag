<template>
  <v-container class="fill-height" fluid>
    <v-row align="center" justify="center">
      <v-col cols="12" sm="8" md="6" lg="4">
        <v-card>
          <v-card-title class="text-h5 text-center pa-4">
            Create Account
          </v-card-title>
          <v-card-text>
            <v-form ref="form" v-model="valid" lazy-validation>
              <v-text-field
                v-model="name"
                :rules="nameRules"
                label="Name"
                required
                prepend-inner-icon="mdi-account"
              ></v-text-field>

              <v-text-field
                v-model="email"
                :rules="emailRules"
                label="Email"
                required
                prepend-inner-icon="mdi-email"
                type="email"
              ></v-text-field>

              <v-text-field
                v-model="password"
                :rules="passwordRules"
                label="Password"
                required
                prepend-inner-icon="mdi-lock"
                :type="showPassword ? 'text' : 'password'"
                :append-inner-icon="showPassword ? 'mdi-eye' : 'mdi-eye-off'"
                @click:append-inner="showPassword = !showPassword"
                hint="Mínimo 8 caracteres, 1 maiúscula, 1 minúscula, 1 especial (!@#$...)"
                persistent-hint
              ></v-text-field>

              <v-text-field
                v-model="confirmPassword"
                :rules="confirmPasswordRules"
                label="Confirm Password"
                required
                prepend-inner-icon="mdi-lock-check"
                :type="showPassword ? 'text' : 'password'"
              ></v-text-field>

              <v-alert v-if="error" type="error" class="mt-4">
                {{ error }}
              </v-alert>

              <v-btn
                :disabled="!valid || loading"
                :loading="loading"
                color="primary"
                block
                class="mt-4"
                @click="register"
              >
                Register
              </v-btn>
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn text to="/login">
              Already have an account? Sign in
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';
import { authService } from '../services/auth';
import { useAuthStore } from '../stores/auth';

export default {
  name: 'Register',
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
        v => !!v || 'Name is required',
        v => (v && v.length >= 3) || 'Name must be at least 3 characters'
      ],
      emailRules: [
        v => !!v || 'Email is required',
        v => /.+@.+\..+/.test(v) || 'Email must be valid'
      ],
      passwordRules: [
        v => !!v || 'Password is required',
        v => !v || v.length >= 8 || 'Mínimo 8 caracteres',
        v => !v || /[A-Z]/.test(v) || 'Deve conter ao menos 1 maiúscula',
        v => !v || /[a-z]/.test(v) || 'Deve conter ao menos 1 minúscula',
        v => !v || /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(v) || 'Deve conter ao menos 1 caractere especial (!@#$...)'
      ],
      confirmPasswordRules: [
        v => !!v || 'Password confirmation is required',
        v => v === this.password || 'Passwords do not match'
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
        // Use createAuthenticate for automatic authentication
        const response = await orionUsersService.createAndAuthenticate(
          this.name,
          this.email,
          this.password
        );

        // Login bem-sucedido
        if (response.authentication && response.authentication.token) {
          const token = response.authentication.token;
          const user = response.authentication.user; // User is inside authentication

          // Create user object with id based on hash
          const userData = user ? {
            ...user,
            id: user.hash || user.email // Usar hash como id, ou email como fallback
          } : null;

          // Update authService (localStorage)
          authService.setToken(token);
          if (userData) {
            authService.setUser(userData);
          }

          // Update authStore (Pinia) to sync with navigation guard
          const authStore = useAuthStore();
          authStore.setToken(token);
          if (userData) {
            authStore.setUser(userData);
          }

          // Wait a bit to ensure store was updated
          await this.$nextTick();

          // Redirecionar para conversas
          this.$router.push('/conversations');
        } else {
          this.error = 'Error creating account. Please try again.';
        }
      } catch (error) {
        console.error('Error registering:', error);
        this.error = error.response?.data?.message || 'Error creating account. Please try again.';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
