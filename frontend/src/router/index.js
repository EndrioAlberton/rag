import { createRouter, createWebHistory } from 'vue-router';
import Login from '../components/Login.vue';
import Register from '../components/Register.vue';
import ChatInterface from '../components/ChatInterface.vue';
import ConversationList from '../components/ConversationList.vue';
import Dashboard from '../components/Dashboard.vue';

const routes = [
  {
    path: '/',
    redirect: '/conversations'
  },
  {
    path: '/register',
    name: 'Register',
    component: Register
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/chat/:conversationId?',
    name: 'Chat',
    component: ChatInterface,
    meta: { requiresAuth: true }
  },
  {
    path: '/conversations',
    name: 'Conversations',
    component: ConversationList,
    meta: { requiresAuth: true }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard,
    meta: { requiresAuth: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;

