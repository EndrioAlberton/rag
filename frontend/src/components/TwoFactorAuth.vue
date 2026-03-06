<template>
  <v-card class="mt-4">
    <v-card-title class="text-h6">
      Two-Factor Authentication
    </v-card-title>
    <v-card-text>
      <p>Please enter the 6-digit code from your authenticator app.</p>
      <v-text-field
        v-model="code"
        label="2FA Code"
        required
        prepend-inner-icon="mdi-shield-lock"
        maxlength="6"
        @keyup.enter="validate"
      ></v-text-field>

      <v-alert v-if="error" type="error" class="mt-4">
        {{ error }}
      </v-alert>

      <v-btn
        :disabled="!code || code.length !== 6 || loading"
        :loading="loading"
        color="primary"
        block
        class="mt-4"
        @click="validate"
      >
        Validate
      </v-btn>
      <v-btn
        text
        block
        class="mt-2"
        @click="$emit('cancel')"
      >
        Cancel
      </v-btn>
    </v-card-text>
  </v-card>
</template>

<script>
import { orionUsersService } from '../services/orionUsers';

export default {
  name: 'TwoFactorAuth',
  props: {
    email: {
      type: String,
      required: true
    }
  },
  emits: ['authenticated', 'cancel'],
  data() {
    return {
      code: '',
      loading: false,
      error: null
    };
  },
  methods: {
    async validate() {
      if (this.code.length !== 6) {
        this.error = 'The code must have 6 digits';
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        const response = await orionUsersService.loginWith2FA(this.email, this.code);

        if (response.authentication && response.authentication.token) {
          // User is inside authentication, not in response.user
          const user = response.authentication.user;
          this.$emit('authenticated', response.authentication.token, user);
        } else {
          this.error = 'Invalid code. Please try again.';
        }
      } catch (error) {
        console.error('Erro ao validar 2FA:', error);
        this.error = error.response?.data?.message || 'Invalid code. Please try again.';
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>

