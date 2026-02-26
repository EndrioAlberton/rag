<template>
  <v-container class="fill-height" fluid>
    <v-row align="center" justify="center">
      <v-col cols="12" sm="8" md="6" lg="4">
        <v-card>
          <v-card-title class="text-h5 text-center pa-4">
            Criar Conta
          </v-card-title>
          <v-card-text>
            <v-form ref="form" v-model="valid" lazy-validation>
              <v-text-field
                v-model="name"
                :rules="nameRules"
                label="Nome"
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
                label="Senha"
                required
                prepend-inner-icon="mdi-lock"
                :type="showPassword ? 'text' : 'password'"
                :append-inner-icon="showPassword ? 'mdi-eye' : 'mdi-eye-off'"
                @click:append-inner="showPassword = !showPassword"
                hint="A senha deve ter no mínimo 8 caracteres, incluindo uma letra maiúscula, um número e um caractere especial"
                persistent-hint
              ></v-text-field>

              <v-text-field
                v-model="confirmPassword"
                :rules="confirmPasswordRules"
                label="Confirmar Senha"
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
                Registrar
              </v-btn>

              <template v-if="isGoogleEnabled">
                <v-divider class="my-4">OU</v-divider>

                <v-btn
                  :disabled="loading || loadingGoogle"
                  :loading="loadingGoogle"
                  color="white"
                  variant="outlined"
                  block
                  @click="registerWithGoogle"
                >
                  <v-icon left>mdi-google</v-icon>
                  Registrar com Google
                </v-btn>
                
                <!-- Mensagem de debug (remover em produção) -->
                <v-alert v-if="!googleInitialized && isGoogleEnabled" type="info" density="compact" class="mt-2" variant="tonal">
                  <small>Inicializando Google Sign In... {{ googleScriptLoaded ? 'Script carregado' : 'Aguardando script...' }}</small>
                </v-alert>
              </template>
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn text to="/login">
              Já tem uma conta? Faça login
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
    // Debug: verificar se a variável de ambiente está sendo carregada
    const rawClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
    const trimmedClientId = (rawClientId || '').trim();
    
    console.log('🔍 Debug Register Component:');
    console.log('  import.meta.env.VITE_GOOGLE_CLIENT_ID (raw):', rawClientId);
    console.log('  import.meta.env.VITE_GOOGLE_CLIENT_ID (trimmed):', trimmedClientId);
    console.log('  Tipo:', typeof rawClientId);
    console.log('  Comprimento:', trimmedClientId.length);
    console.log('  Todas as variáveis VITE_*:', Object.keys(import.meta.env).filter(k => k.startsWith('VITE_')));
    
    return {
      valid: false,
      name: '',
      email: '',
      password: '',
      confirmPassword: '',
      showPassword: false,
      loading: false,
      loadingGoogle: false,
      error: null,
      googleClientId: trimmedClientId,
      googleScriptLoaded: false,
      googleInitialized: false,
      nameRules: [
        v => !!v || 'Nome é obrigatório',
        v => (v && v.length >= 3) || 'Nome deve ter pelo menos 3 caracteres'
      ],
      emailRules: [
        v => !!v || 'Email é obrigatório',
        v => /.+@.+\..+/.test(v) || 'Email deve ser válido'
      ],
      passwordRules: [
        v => !!v || 'Senha é obrigatória',
        v => !v || (v && v.length >= 8) || 'Senha deve ter no mínimo 8 caracteres',
        v => !v || (v && /[A-Z]/.test(v)) || 'Senha deve conter pelo menos uma letra maiúscula',
        v => !v || (v && /[0-9]/.test(v)) || 'Senha deve conter pelo menos um número',
        v => !v || (v && /[^A-Za-z0-9]/.test(v)) || 'Senha deve conter pelo menos um caractere especial'
      ],
      confirmPasswordRules: [
        v => !!v || 'Confirmação de senha é obrigatória',
        v => v === this.password || 'Senhas não coincidem'
      ]
    };
  },
  computed: {
    isGoogleEnabled() {
      const enabled = !!this.googleClientId && this.googleClientId.length > 0;
      console.log('🔍 isGoogleEnabled:', enabled, '| googleClientId:', this.googleClientId ? `"${this.googleClientId.substring(0, 20)}..."` : '(vazio)');
      return enabled;
    }
  },
  mounted() {
    if (this.isGoogleEnabled) {
      this.waitForGoogleScript();
    } else {
      console.warn('Google Client ID não configurado. Registro com Google desabilitado.');
    }
  },
  methods: {
    async register() {
      if (!this.$refs.form.validate()) {
        return;
      }

      this.loading = true;
      this.error = null;

      try {
        // Usar createAuthenticate para autenticação automática
        const response = await orionUsersService.createAndAuthenticate(
          this.name,
          this.email,
          this.password
        );

        // Verificar se 2FA é necessário
        if (response.requires2FA) {
          this.error = 'Autenticação de dois fatores necessária. Por favor, faça login.';
          this.loading = false;
          return;
        }

        // Login bem-sucedido
        if (response.authentication && response.authentication.token) {
          const token = response.authentication.token;
          const user = response.authentication.user; // Usuário está dentro de authentication
          
          // Criar objeto de usuário com id baseado no hash
          const userData = user ? {
            ...user,
            id: user.hash || user.email // Usar hash como id, ou email como fallback
          } : null;
          
          console.log('Registro bem-sucedido. Token:', token ? 'recebido' : 'não recebido');
          console.log('Usuário:', userData);
          
          // Atualizar authService (localStorage)
          authService.setToken(token);
          if (userData) {
            authService.setUser(userData);
          }
          
          // Atualizar authStore (Pinia) para sincronizar com o guard de navegação
          const authStore = useAuthStore();
          authStore.setToken(token);
          if (userData) {
            authStore.setUser(userData);
          }
          
          // Aguardar um pouco para garantir que o store foi atualizado
          await this.$nextTick();
          
          // Redirecionar para conversas
          this.$router.push('/conversations');
        } else {
          this.error = 'Erro ao criar conta. Tente novamente.';
        }
      } catch (error) {
        console.error('Erro ao registrar:', error);
        this.error = error.response?.data?.message || 'Erro ao criar conta. Tente novamente.';
      } finally {
        this.loading = false;
      }
    },

    waitForGoogleScript() {
      // Verificar se o script já está carregado
      if (typeof window.google !== 'undefined' && window.google.accounts) {
        this.googleScriptLoaded = true;
        this.initializeGoogleSignIn();
        return;
      }

      // Aguardar o script carregar (máximo 10 segundos)
      let attempts = 0;
      const maxAttempts = 50; // 50 tentativas x 200ms = 10 segundos
      
      const checkInterval = setInterval(() => {
        attempts++;
        
        if (typeof window.google !== 'undefined' && window.google.accounts) {
          this.googleScriptLoaded = true;
          clearInterval(checkInterval);
          this.initializeGoogleSignIn();
        } else if (attempts >= maxAttempts) {
          clearInterval(checkInterval);
          console.error('Google Identity Services não carregou após 10 segundos');
          this.error = 'Erro ao carregar Google Identity Services. Verifique sua conexão.';
        }
      }, 200);
    },

    initializeGoogleSignIn() {
      if (!this.isGoogleEnabled) {
        console.warn('Google Client ID não configurado');
        return;
      }

      if (typeof window.google === 'undefined' || !window.google.accounts) {
        console.error('Google Identity Services não disponível');
        return;
      }

      try {
        window.google.accounts.id.initialize({
          client_id: this.googleClientId,
          callback: this.handleGoogleCredentialResponse,
          auto_select: false,
          cancel_on_tap_outside: true
        });
        this.googleInitialized = true;
        console.log('Google Sign In inicializado com sucesso');
      } catch (error) {
        console.error('Erro ao inicializar Google Sign In:', error);
        this.error = 'Erro ao inicializar autenticação Google. Tente recarregar a página.';
      }
    },

    async registerWithGoogle() {
      if (!this.isGoogleEnabled) {
        this.error = 'Google Client ID não configurado. Verifique o arquivo .env e reinicie o servidor.';
        return;
      }

      // Se não inicializado, tentar inicializar primeiro
      if (!this.googleInitialized) {
        console.log('Google Sign In não inicializado. Tentando inicializar...');
        if (typeof window.google === 'undefined' || !window.google.accounts) {
          // Script ainda não carregou, aguardar
          this.error = 'Aguardando carregamento do Google Identity Services...';
          this.waitForGoogleScript();
          // Tentar novamente após um delay
          setTimeout(() => {
            if (typeof window.google !== 'undefined' && window.google.accounts) {
              this.initializeGoogleSignIn();
              // Tentar registro novamente após inicialização
              setTimeout(() => this.registerWithGoogle(), 500);
            }
          }, 2000);
          return;
        } else {
          // Script carregou mas não inicializou, inicializar agora
          this.initializeGoogleSignIn();
          // Aguardar um pouco e tentar novamente
          setTimeout(() => this.registerWithGoogle(), 500);
          return;
        }
      }

      if (!this.googleScriptLoaded || typeof window.google === 'undefined' || !window.google.accounts) {
        this.error = 'Google Identity Services não carregado. Aguarde alguns segundos e tente novamente.';
        // Tentar recarregar
        this.waitForGoogleScript();
        return;
      }

      this.loadingGoogle = true;
      this.error = null;

      try {
        // Tentar usar Google One Tap primeiro (retorna idToken diretamente)
        window.google.accounts.id.prompt((notification) => {
          if (notification.isNotDisplayed()) {
            // One Tap não disponível, usar botão renderizado
            this.renderGoogleButton();
          } else if (notification.isSkippedMoment() || notification.isDismissedMoment()) {
            // Usuário ignorou One Tap, usar botão renderizado
            this.renderGoogleButton();
          }
        });
      } catch (error) {
        console.error('Erro ao tentar Google One Tap:', error);
        // Fallback para botão renderizado
        this.renderGoogleButton();
      }
    },

    renderGoogleButton() {
      // Criar container para o botão do Google
      const buttonContainer = document.createElement('div');
      buttonContainer.id = 'google-signin-button';
      buttonContainer.style.position = 'fixed';
      buttonContainer.style.top = '50%';
      buttonContainer.style.left = '50%';
      buttonContainer.style.transform = 'translate(-50%, -50%)';
      buttonContainer.style.zIndex = '9999';
      document.body.appendChild(buttonContainer);

      try {
        window.google.accounts.id.renderButton(
          buttonContainer,
          {
            type: 'standard',
            theme: 'outline',
            size: 'large',
            text: 'signin_with',
            shape: 'rectangular',
            logo_alignment: 'left',
            width: '300'
          }
        );

        // O callback handleGoogleCredentialResponse será chamado automaticamente
        // quando o usuário clicar no botão e autenticar
      } catch (error) {
        console.error('Erro ao renderizar botão Google:', error);
        this.error = 'Erro ao inicializar autenticação Google. Tente novamente.';
        this.loadingGoogle = false;
        if (buttonContainer.parentNode) {
          buttonContainer.parentNode.removeChild(buttonContainer);
        }
      }
    },

    async handleGoogleCredentialResponse(response) {
      // Remover botão do Google se existir
      const buttonContainer = document.getElementById('google-signin-button');
      if (buttonContainer && buttonContainer.parentNode) {
        buttonContainer.parentNode.removeChild(buttonContainer);
      }

      if (response.credential) {
        await this.processGoogleLogin(response.credential);
      } else if (response.error) {
        this.error = 'Erro na autenticação Google: ' + response.error;
        this.loadingGoogle = false;
      }
    },

    async processGoogleLogin(token) {
      // Remover botão do Google se existir
      const buttonContainer = document.getElementById('google-signin-button');
      if (buttonContainer && buttonContainer.parentNode) {
        buttonContainer.parentNode.removeChild(buttonContainer);
      }

      try {
        const response = await orionUsersService.loginWithGoogle(token);

        // Verificar se 2FA é necessário
        if (response.requires2FA) {
          this.error = 'Autenticação de dois fatores necessária. Por favor, faça login.';
          this.loadingGoogle = false;
          return;
        }

        // Login bem-sucedido (o login social cria o usuário automaticamente se não existir)
        if (response.authentication && response.authentication.token) {
          const jwtToken = response.authentication.token;
          const user = response.authentication.user; // Usuário está dentro de authentication
          
          // Criar objeto de usuário com id baseado no hash
          const userData = user ? {
            ...user,
            id: user.hash || user.email
          } : null;
          
          console.log('Registro/Login Google bem-sucedido. Token:', jwtToken ? 'recebido' : 'não recebido');
          console.log('Usuário:', userData);
          
          // Atualizar authService (localStorage)
          authService.setToken(jwtToken);
          if (userData) {
            authService.setUser(userData);
          }
          
          // Atualizar authStore (Pinia)
          const authStore = useAuthStore();
          authStore.setToken(jwtToken);
          if (userData) {
            authStore.setUser(userData);
          }
          
          await this.$nextTick();
          
          // Redirecionar para conversas
          this.$router.push('/conversations');
        } else {
          this.error = 'Erro ao registrar/login com Google. Tente novamente.';
        }
      } catch (error) {
        console.error('Erro ao registrar/login com Google:', error);
        this.error = error.response?.data?.message || 'Erro ao registrar/login com Google. Tente novamente.';
      } finally {
        this.loadingGoogle = false;
      }
    }
  }
};
</script>

