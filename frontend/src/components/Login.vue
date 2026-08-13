<template>
  <v-container class="fill-height" fluid>
    <v-row align="center" justify="center">
      <v-col cols="12" sm="8" md="6" lg="4">
        <v-card>
          <v-card-title class="text-h5 text-center pa-4">
            Login
          </v-card-title>
          <v-card-text>
            <v-form ref="form" v-model="valid" lazy-validation>
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
                @keyup.enter="login"
                hint="Password must be at least 8 characters"
                persistent-hint
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
                @click="login"
              >
                Sign In
              </v-btn>
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn text to="/register">
              Don't have an account? Sign up
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
  name: 'Login',
  data() {
    return {
      valid: false,
      email: '',
      password: '',
      showPassword: false,
      loading: false,
      error: null,
      emailRules: [
        v => !!v || 'Email is required',
        v => /.+@.+\..+/.test(v) || 'Email must be valid'
      ],
      passwordRules: [
        v => !!v || 'Password is required',
        v => !v || (v && v.length >= 8) || 'Password must be at least 8 characters'
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

          // Redirect to conversations
          this.$router.push('/conversations');
        } else {
          this.error = 'Error signing in. Please try again.';
        }
      } catch (error) {
        console.error('Error signing in:', error);
        this.error = error.response?.data?.message || 'Error signing in. Please check your credentials.';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
