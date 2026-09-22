<template>
  <div class="auth-screen">
    <div class="auth-hero">
      <div class="mark">
        <v-icon size="34" color="white">mdi-grid</v-icon>
        <div class="mark-text mark-text-hero">
          <b>Consulta AI</b>
          <span>Atendimento SSI · IFRS</span>
        </div>
      </div>
      <div class="auth-hero-copy">
        <h1>{{ heroTitle }}</h1>
        <p>{{ heroText }}</p>
      </div>
    </div>

    <div class="auth-panel">
      <div class="auth-card">
        <div class="mark mark-mobile">
          <v-icon size="30" color="primary">mdi-grid</v-icon>
          <div class="mark-text">
            <b>Consulta AI</b>
            <span>Atendimento SSI · IFRS</span>
          </div>
        </div>
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AuthLayout',
  props: {
    heroTitle: {
      type: String,
      default: 'Atendimento virtual do curso de Sistemas para Internet do IFRS.'
    },
    heroText: {
      type: String,
      default: 'Dúvidas sobre grade curricular, TCC, matrícula ou calendário acadêmico? Pergunte direto ao assistente oficial do curso, baseado na documentação do IFRS.'
    }
  }
};
</script>

<style scoped>
.auth-screen {
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
}
@media (max-width: 860px) {
  .auth-screen { grid-template-columns: 1fr; }
  .auth-hero { display: none; }
}
.auth-hero {
  position: relative;
  background: radial-gradient(120% 140% at 15% 10%, var(--verde-600), var(--verde-900) 65%);
  color: #EFFBF2;
  padding: 56px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 40px;
}
.mark { display: inline-flex; align-items: center; gap: 10px; }
.mark-text { display: flex; flex-direction: column; line-height: 1.05; }
.mark-text b { font-family: 'Fraunces', Georgia, serif; font-weight: 700; font-size: 1.05rem; color: var(--ink-900); }
.mark-text span { font-size: 0.72rem; color: var(--ink-500); }
.mark-text-hero b, .mark-text-hero span { color: #EFFBF2; }
.mark-text-hero span { color: rgba(239,251,242,.75); }
.auth-hero-copy { max-width: 460px; }
.auth-hero-copy h1 {
  font-family: 'Fraunces', Georgia, serif;
  font-size: clamp(2rem, 3.4vw, 2.7rem);
  font-weight: 600;
  line-height: 1.15;
  margin: 0 0 18px;
}
.auth-hero-copy p {
  font-size: 1.02rem;
  line-height: 1.6;
  color: rgba(239,251,242,.85);
  margin: 0;
  max-width: 40ch;
}
.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 28px;
}
.auth-card { width: 100%; max-width: 380px; }
.mark-mobile { margin-bottom: 32px; }
@media (min-width: 861px) { .mark-mobile { display: none; } }
</style>
