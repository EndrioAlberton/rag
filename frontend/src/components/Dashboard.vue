<template>
  <div class="pa-4" style="max-width: 1100px; margin: 0 auto;">
    <h2 class="text-h5 mb-4">Dashboard</h2>

    <v-alert v-if="error" type="error" closable class="mb-4" @click:close="error = null">
      {{ error }}
    </v-alert>

    <div v-if="loading" class="text-center mt-6">
      <v-progress-circular indeterminate color="primary"></v-progress-circular>
    </div>

    <div v-else class="d-flex flex-wrap" style="gap: 12px;">
      <!-- Total -->
      <v-card class="pa-3" style="min-width: 220px;">
        <div class="text-caption">Total interações</div>
        <div class="text-h6">{{ metrics.totalRequests }}</div>
      </v-card>

      <!-- Conversas -->
      <v-card class="pa-3" style="min-width: 220px;">
        <div class="text-caption">Conversas</div>
        <div class="text-h6">{{ metrics.totalConversations }}</div>
      </v-card>

      <!-- Handoff -->
      <v-card class="pa-3" style="min-width: 220px;">
        <div class="text-caption">Encaminhadas (suporte humano)</div>
        <div class="text-h6">{{ metrics.handoffRequired }}</div>
      </v-card>

      <!-- Likes / Dislikes - clicáveis -->
      <v-card
        class="pa-3 cursor-pointer"
        style="min-width: 220px;"
        :class="{ 'card-active': detail.type === 'LIKE' }"
        @click="loadDetail('LIKE')"
      >
        <div class="text-caption">
          👍 Likes
          <v-icon size="small" color="grey" end>mdi-chevron-right</v-icon>
        </div>
        <div class="text-h6">{{ metrics.likes }}</div>
      </v-card>

      <v-card
        class="pa-3 cursor-pointer"
        style="min-width: 220px;"
        :class="{ 'card-active': detail.type === 'DISLIKE' }"
        @click="loadDetail('DISLIKE')"
      >
        <div class="text-caption">
          👎 Dislikes
          <v-icon size="small" color="grey" end>mdi-chevron-right</v-icon>
        </div>
        <div class="text-h6">{{ metrics.dislikes }}</div>
      </v-card>

      <!-- Urgências - clicáveis -->
      <v-card
        class="pa-3 cursor-pointer"
        style="min-width: 220px;"
        :class="{ 'card-active': detail.type === 'urgency-BAIXA' }"
        @click="loadDetail('urgency-BAIXA')"
      >
        <div class="text-caption">
          🟢 Urgência Baixa
          <v-icon size="small" color="grey" end>mdi-chevron-right</v-icon>
        </div>
        <div class="text-h6">{{ metrics.urgencyLow }}</div>
      </v-card>

      <v-card
        class="pa-3 cursor-pointer"
        style="min-width: 220px;"
        :class="{ 'card-active': detail.type === 'urgency-MEDIA' }"
        @click="loadDetail('urgency-MEDIA')"
      >
        <div class="text-caption">
          🟡 Urgência Média
          <v-icon size="small" color="grey" end>mdi-chevron-right</v-icon>
        </div>
        <div class="text-h6">{{ metrics.urgencyMedium }}</div>
      </v-card>

      <v-card
        class="pa-3 cursor-pointer"
        style="min-width: 220px;"
        :class="{ 'card-active': detail.type === 'urgency-ALTA' }"
        @click="loadDetail('urgency-ALTA')"
      >
        <div class="text-caption">
          🔴 Urgência Alta
          <v-icon size="small" color="grey" end>mdi-chevron-right</v-icon>
        </div>
        <div class="text-h6">{{ metrics.urgencyHigh }}</div>
      </v-card>
    </div>

    <!-- Painel de detalhe de interações -->
    <v-expand-transition>
      <div v-if="detail.visible" class="mt-6">
        <v-card>
          <v-card-title class="d-flex justify-space-between align-center">
            <span>{{ detail.title }}</span>
            <v-btn icon="mdi-close" variant="text" size="small" @click="closeDetail" />
          </v-card-title>

          <v-card-text>
            <div v-if="detail.loading" class="text-center py-4">
              <v-progress-circular indeterminate color="primary"></v-progress-circular>
            </div>
            <div v-else-if="detail.items.length === 0" class="text-center text-grey py-4">
              Nenhuma interação encontrada.
            </div>
            <v-list v-else lines="three" density="compact">
              <v-list-item
                v-for="item in detail.items"
                :key="item.id"
                class="mb-2 border-b"
              >
                <template #title>
                  <span class="text-body-2 font-weight-medium">👤 {{ item.userMessage }}</span>
                </template>
                <template #subtitle>
                  <div class="text-body-2 mt-1" style="white-space: pre-wrap;">
                    🤖 {{ item.llmResponse ? item.llmResponse.slice(0, 300) + (item.llmResponse.length > 300 ? '…' : '') : '—' }}
                  </div>
                  <div class="text-caption text-grey mt-1">{{ item.createdAt }}</div>
                </template>
              </v-list-item>
            </v-list>
          </v-card-text>
        </v-card>
      </div>
    </v-expand-transition>
  </div>
</template>

<script>
import { apiService } from '../services/api';

const DETAIL_TITLES = {
  'LIKE': '👍 Interações com Like',
  'DISLIKE': '👎 Interações com Dislike',
  'urgency-BAIXA': '🟢 Interações - Urgência Baixa',
  'urgency-MEDIA': '🟡 Interações - Urgência Média',
  'urgency-ALTA': '🔴 Interações - Urgência Alta',
};

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
        dislikes: 0,
        urgencyLow: 0,
        urgencyMedium: 0,
        urgencyHigh: 0,
      },
      detail: {
        visible: false,
        loading: false,
        type: null,
        title: '',
        items: [],
      },
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
  },
  methods: {
    async loadDetail(type) {
      // Toggle: clicar no mesmo fecha o painel
      if (this.detail.type === type && this.detail.visible) {
        this.closeDetail();
        return;
      }

      this.detail.visible = true;
      this.detail.type = type;
      this.detail.title = DETAIL_TITLES[type] || type;
      this.detail.loading = true;
      this.detail.items = [];

      try {
        if (type === 'LIKE' || type === 'DISLIKE') {
          this.detail.items = await apiService.getDashboardInteractionsByFeedback(type);
        } else if (type.startsWith('urgency-')) {
          const urgency = type.replace('urgency-', '');
          this.detail.items = await apiService.getDashboardInteractionsByUrgency(urgency);
        }
      } catch (e) {
        this.error = e.message || 'Erro ao carregar interações';
        this.detail.visible = false;
      } finally {
        this.detail.loading = false;
      }
    },
    closeDetail() {
      this.detail.visible = false;
      this.detail.type = null;
      this.detail.items = [];
    },
  },
};
</script>

<style scoped>
.cursor-pointer {
  cursor: pointer;
  transition: box-shadow 0.15s;
}
.cursor-pointer:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.card-active {
  outline: 2px solid rgb(var(--v-theme-primary));
}
.border-b {
  border-bottom: 1px solid rgba(0,0,0,0.08);
}
</style>

