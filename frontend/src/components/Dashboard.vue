<template>
  <div class="pa-4" style="max-width: 900px; margin: 0 auto;">
    <h2 class="text-h5 mb-4">Dashboard</h2>

    <v-alert v-if="error" type="error" dismissible class="mb-4" @click:close="error = null">
      {{ error }}
    </v-alert>

    <div v-if="loading" class="text-center mt-6">
      <v-progress-circular indeterminate color="primary"></v-progress-circular>
    </div>

    <div v-else class="d-flex flex-wrap" style="gap: 12px;">
      <v-card class="pa-3" style="min-width: 220px;">
        <div class="text-caption">Total interações</div>
        <div class="text-h6">{{ metrics.totalRequests }}</div>
      </v-card>

      <v-card class="pa-3" style="min-width: 220px;">
        <div class="text-caption">Conversas</div>
        <div class="text-h6">{{ metrics.totalConversations }}</div>
      </v-card>

      <v-card class="pa-3" style="min-width: 220px;">
        <div class="text-caption">Encaminhadas (suporte humano)</div>
        <div class="text-h6">{{ metrics.handoffRequired }}</div>
      </v-card>

      <v-card class="pa-3" style="min-width: 220px;">
        <div class="text-caption">Likes / Dislikes</div>
        <div class="text-h6">{{ metrics.likes }} / {{ metrics.dislikes }}</div>
      </v-card>
    </div>
  </div>
</template>

<script>
import { apiService } from '../services/api';

export default {
  name: 'Dashboard',
  data() {
    return {
      loading: true,
      error: null,
      metrics: {
        totalRequests: 0,
        totalConversations: 0,
        handoffRequired: 0,
        likes: 0,
        dislikes: 0
      }
    };
  },
  async mounted() {
    try {
      this.loading = true;
      this.metrics = await apiService.getDashboardMetrics();
    } catch (e) {
      this.error = e.message || 'Erro ao carregar métricas';
    } finally {
      this.loading = false;
    }
  }
};
</script>

