import axios from 'axios';

// Orion Users API Service
const ORION_USERS_URL = import.meta.env.VITE_ORION_USERS_URL || 'http://localhost:8080';

const orionUsersApi = axios.create({
  baseURL: ORION_USERS_URL,
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded'
  }
});

export const orionUsersService = {
  // Registrar usuário
  async createUser(name, email, password) {
    const formData = new URLSearchParams();
    formData.append('name', name);
    formData.append('email', email);
    formData.append('password', password);
    
    const response = await orionUsersApi.post('/users/create', formData);
    return response.data;
  },

  // Registrar e autenticar em uma única requisição
  async createAndAuthenticate(name, email, password) {
    const formData = new URLSearchParams();
    formData.append('name', name);
    formData.append('email', email);
    formData.append('password', password);
    
    const response = await orionUsersApi.post('/users/createAuthenticate', formData);
    return response.data;
  },

  // Login
  async login(email, password) {
    const formData = new URLSearchParams();
    formData.append('email', email);
    formData.append('password', password);
    
    const response = await orionUsersApi.post('/users/login', formData);
    return response.data;
  },

  // Login com Google (Social Authentication)
  async loginWithGoogle(idToken) {
    const formData = new URLSearchParams();
    formData.append('idToken', idToken);
    
    const response = await orionUsersApi.post('/users/login/google', formData);
    return response.data;
  },

  // Login com 2FA
  async loginWith2FA(email, code) {
    const formData = new URLSearchParams();
    formData.append('email', email);
    formData.append('code', code);
    
    const response = await orionUsersApi.post('/users/login/2fa', formData);
    return response.data;
  },

  // Obter QR code para 2FA
  async getQRCode(email, password) {
    const formData = new URLSearchParams();
    formData.append('email', email);
    formData.append('password', password);
    
    const response = await orionUsersApi.post('/users/google/2FAuth/qrCode', formData, {
      responseType: 'blob'
    });
    return response.data;
  },

  // Validar código 2FA
  async validate2FA(email, password, code) {
    const formData = new URLSearchParams();
    formData.append('email', email);
    formData.append('password', password);
    formData.append('code', code);
    
    const response = await orionUsersApi.post('/users/google/2FAuth/validate', formData);
    return response.data;
  },

  // Atualizar configurações 2FA
  async update2FASettings(email, require2FAForBasicLogin, require2FAForSocialLogin, token) {
    const formData = new URLSearchParams();
    formData.append('email', email);
    if (require2FAForBasicLogin !== undefined) {
      formData.append('require2FAForBasicLogin', require2FAForBasicLogin);
    }
    if (require2FAForSocialLogin !== undefined) {
      formData.append('require2FAForSocialLogin', require2FAForSocialLogin);
    }
    
    const response = await orionUsersApi.post('/users/2fa/settings', formData, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    return response.data;
  }
};

