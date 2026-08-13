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
  }
};
