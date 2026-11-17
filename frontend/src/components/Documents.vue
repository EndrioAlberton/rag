<template>
  <div class="pa-4" style="max-width: 700px; margin: 0 auto;">
    <h2 class="text-h5 mb-2">Documentos</h2>
    <p class="text-body-2 mb-4">
      Envie PDF, Markdown ou TXT para a base de conhecimento. Enviar um arquivo com o mesmo nome substitui a versão anterior.
    </p>

    <v-file-input
      v-model="files"
      label="Arquivos"
      accept=".pdf,.md,.txt"
      multiple
      show-size
      prepend-icon="mdi-paperclip"
    ></v-file-input>

    <v-btn color="primary" :loading="loading" :disabled="!files.length" @click="send">Enviar</v-btn>

    <v-alert v-if="error" type="error" class="mt-4">{{ error }}</v-alert>
    <v-alert v-if="result" type="success" class="mt-4">
      Ingeridos: {{ result.ingested.join(', ') || 'nenhum' }}
      <div v-if="result.rejected.length">Rejeitados (formato inválido ou sem texto): {{ result.rejected.join(', ') }}</div>
    </v-alert>
  </div>
</template>

<script>
import { apiService } from '../services/api';

export default {
  name: 'Documents',
  data() {
    return { files: [], loading: false, error: null, result: null };
  },
  methods: {
    async send() {
      this.loading = true;
      this.error = null;
      this.result = null;
      try {
        this.result = await apiService.uploadDocuments(this.files);
        this.files = [];
      } catch (e) {
        this.error = e.response?.data?.message || e.message || 'Erro ao enviar';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
