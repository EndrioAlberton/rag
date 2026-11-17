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

    <h3 class="text-h6 mt-8 mb-2">Base atual ({{ sources.length }})</h3>
    <v-text-field v-model="filter" label="Filtrar" density="compact" clearable hide-details class="mb-2"></v-text-field>
    <v-table density="compact">
      <tbody>
        <tr v-for="s in filtered" :key="s.source">
          <td style="word-break: break-all">{{ s.source }}</td>
          <td class="text-no-wrap">{{ fmt(s.ingestedAt) }}</td>
          <td class="text-right">
            <v-btn icon="mdi-delete-outline" size="small" variant="text" color="error" title="Remover" @click="remove(s.source)"></v-btn>
          </td>
        </tr>
      </tbody>
    </v-table>
  </div>
</template>

<script>
import { apiService } from '../services/api';

export default {
  name: 'Documents',
  data() {
    return { files: [], loading: false, error: null, result: null, sources: [], filter: '' };
  },
  computed: {
    filtered() {
      const f = (this.filter || '').toLowerCase();
      return this.sources.filter((s) => s.source.toLowerCase().includes(f));
    },
  },
  mounted() {
    this.load();
  },
  methods: {
    fmt(d) {
      return d ? new Date(d).toLocaleString('pt-BR') : '';
    },
    async load() {
      try {
        this.sources = await apiService.listDocuments();
      } catch (e) {
        this.error = e.message || 'Erro ao listar documentos';
      }
    },
    async remove(name) {
      if (!confirm(`Remover "${name}" da base de conhecimento?`)) return;
      try {
        await apiService.deleteDocument(name);
        await this.load();
      } catch (e) {
        this.error = e.message || 'Erro ao remover';
      }
    },
    async send() {
      this.loading = true;
      this.error = null;
      this.result = null;
      try {
        this.result = await apiService.uploadDocuments(this.files);
        this.files = [];
        await this.load();
      } catch (e) {
        this.error = e.response?.data?.message || e.message || 'Erro ao enviar';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
