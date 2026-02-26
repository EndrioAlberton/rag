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
  const googleClientId = env.VITE_GOOGLE_CLIENT_ID || '';
  const hasGoogleClientId = googleClientId.trim().length > 0;
  
  console.log('🔧 Variáveis de ambiente carregadas (' + mode + '):');
  console.log('  VITE_API_BASE_URL:', env.VITE_API_BASE_URL || '(não definido - usando padrão)');
  console.log('  VITE_ORION_USERS_URL:', env.VITE_ORION_USERS_URL || '(não definido - usando padrão)');
  if (hasGoogleClientId) {
    console.log('  VITE_GOOGLE_CLIENT_ID: ✅ configurado (' + googleClientId.substring(0, 20) + '...)');
  } else {
    console.log('  VITE_GOOGLE_CLIENT_ID: ❌ NÃO DEFINIDO - Botão de login Google será oculto');
    console.log('  💡 Para habilitar, adicione VITE_GOOGLE_CLIENT_ID no arquivo frontend/.env');
  }
  
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

