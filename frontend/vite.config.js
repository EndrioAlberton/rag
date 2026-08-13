import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import vuetify from 'vite-plugin-vuetify';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig(({ mode }) => {
  // Obter o diretório raiz do projeto (onde está o vite.config.js e o .env)
  const root = fileURLToPath(new URL('.', import.meta.url));
  
  // Carregar variáveis de ambiente do arquivo .env
  // O Vite automaticamente carrega .env, mas loadEnv garante que funcione em todos os modos
  // O terceiro parâmetro '' significa carregar todas as variáveis (não apenas VITE_)
  // mas o Vite só expõe variáveis que começam com VITE_ para o código do cliente
  const env = loadEnv(mode, root, '');

  // Log para debug (tanto em desenvolvimento quanto em build)
  console.log('🔧 Environment variables loaded (' + mode + '):');
  console.log('  VITE_API_BASE_URL:', env.VITE_API_BASE_URL || '(not defined - using default)');
  console.log('  VITE_ORION_USERS_URL:', env.VITE_ORION_USERS_URL || '(not defined - using default)');

  return {
    root: root,
    plugins: [
      vue(),
      vuetify({ autoImport: true })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    build: {
      outDir: '../src/main/resources/META-INF/resources',
      emptyOutDir: true,
      rollupOptions: {
        input: {
          main: fileURLToPath(new URL('./index.html', import.meta.url))
        }
      }
    },
    server: {
      port: 5173,
      proxy: {
        '/ai': {
          target: 'http://localhost:8081',
          changeOrigin: true
        }
      }
    }
  };
});

