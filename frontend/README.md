# Frontend RAG Chatbot

This is the frontend for the RAG Chatbot application, built with Vue.js 3, Vuetify and Vite. The frontend provides a modern and responsive interface to interact with the RAG system through a conversational chatbot.

## ⚡ Quick Start

```bash
# 1. Install dependencies
cd frontend
npm install

# 2. Configure environment variables
# Create a .env file in the frontend directory root (see section below)

# 3. Start development server
npm run dev

# Access http://localhost:5173
```

**⚠️ Important:** Do not forget to create the `.env` file with the required variables, especially `VITE_GOOGLE_CLIENT_ID` if you want to use Google login.

## 🛠️ Technologies Used

- **Vue.js 3** - Progressive JavaScript framework for building interfaces
- **Vuetify 3** - Material Design component framework for Vue
- **Vite** - Build tool and fast development server
- **Vue Router** - Official routing for Vue.js
- **Pinia** - State management store for Vue
- **Axios** - HTTP client for API requests
- **Marked** - Markdown parser for message rendering
- **Highlight.js** - Syntax highlighting for code blocks

## 📋 Prerequisites

- **Node.js** 18+ and npm
- Backend services running:
  - RAG API (default: `http://localhost:8081`)
  - Orion Users Service (default: `http://localhost:8080`)

## 🚀 Installation and Configuration

### 1. Dependency Installation

```bash
cd frontend
npm install
```

### 2. Environment Variables Configuration

Create a `.env` file in the `frontend` directory root with the following variables:

```bash
# In frontend directory
touch .env
```

Minimum `.env` file content:

```env
# RAG API base URL
VITE_API_BASE_URL=http://localhost:8081

# Orion Users service URL (authentication)
VITE_ORION_USERS_URL=http://localhost:8080

# Google Client ID for social authentication (required for Google login)
VITE_GOOGLE_CLIENT_ID=your-google-client-id-here
```

**⚠️ Important:**
- All variables must start with `VITE_` to be exposed to frontend code by Vite
- Environment variables are **embedded in the code during build** - they are not read at runtime
- After creating or modifying the `.env` file, you **MUST**:
  - Restart the development server (`npm run dev`) if using Vite dev server
  - Do a new build (`npm run build`) if using the frontend served by Quarkus
- Do not commit the `.env` file to git (it should be in `.gitignore`)
- Use different values for development, staging and production

### 3. Google Client ID Configuration

To enable Google login, you need to:

1. Access [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the "Google Identity Services" API
4. Create OAuth 2.0 credentials:
   - Type: **OAuth 2.0 Client ID**
   - Application type: **Web application**
   - Authorized redirect URIs: `http://localhost:5173` (development)
   - Authorized JavaScript origins: `http://localhost:5173`
5. Copy the generated **Client ID**
6. Add to the `.env` file:

```env
VITE_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
```

## 🔧 Development

To run the frontend in development mode:

```bash
cd frontend
npm install
npm run dev
```

The development server will be available at `http://localhost:5173` with hot-reload enabled.

### Available Commands

```bash
# Development mode
npm run dev

# Production build
npm run build

# Production build preview
npm run preview
```

## 🏗️ Production Build

The frontend build is generated in `../src/main/resources/META-INF/resources/` to be served directly by Quarkus:

```bash
cd frontend
npm run build
```

After the build, static files will be available for Quarkus to serve.

**⚠️ Important about Environment Variables:**

Vite environment variables (such as `VITE_GOOGLE_CLIENT_ID`) are **embedded in the code during build**. This means that:

1. **You MUST build BEFORE running Quarkus dev** if you want to use the web interface served by Quarkus
2. **Variables must be configured in `frontend/.env` BEFORE the build**
3. **If you modify environment variables, a new build is required** for changes to take effect
4. **In Quarkus dev mode**, the frontend is served from built files, not the Vite server

**Example flow:**

```bash
# 1. Configure environment variables
cd frontend
# Edit or create .env file with VITE_GOOGLE_CLIENT_ID, etc.

# 2. Build
npm run build

# 3. Run Quarkus (in another terminal or after returning to root)
cd ..
./mvnw quarkus:dev
```

**Note:** The output directory is automatically cleaned before each build (`emptyOutDir: true`).

## ⚙️ Environment Variables

### Available Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `VITE_API_BASE_URL` | RAG backend API base URL | `http://localhost:8081` | No |
| `VITE_ORION_USERS_URL` | Orion Users service URL (authentication) | `http://localhost:8080` | No |
| `VITE_GOOGLE_CLIENT_ID` | Google Client ID for social authentication | - | Yes (for Google login) |

### Complete `.env` File Example

```env
# ============================================
# Backend Service URLs
# ============================================

# RAG API base URL (main chat and processing service)
# Default: http://localhost:8081
VITE_API_BASE_URL=http://localhost:8081

# Orion Users service URL (authentication and user management)
# Default: http://localhost:8080
VITE_ORION_USERS_URL=http://localhost:8080

# ============================================
# Social Authentication (Google)
# ============================================

# Google Client ID for social authentication
# REQUIRED to enable Google login
# Expected format: xxxxx-xxxxx.apps.googleusercontent.com
# 
# How to obtain:
# 1. Access https://console.cloud.google.com/
# 2. Create a project or select an existing one
# 3. Enable "Google Identity Services" API
# 4. Create OAuth 2.0 credentials:
#    - Type: OAuth 2.0 Client ID
#    - Application type: Web application
#    - Redirect URIs: http://localhost:5173 (dev)
#    - JavaScript origins: http://localhost:5173 (dev)
# 5. Copy the generated Client ID and paste below
#
# Leave empty if you do not want to use Google login
VITE_GOOGLE_CLIENT_ID=123456789-abcdefghijklmnop.apps.googleusercontent.com
```

**Note:** For other environments (staging/production), adjust values as needed.

## 📁 Project Structure

```
frontend/
├── src/
│   ├── components/          # Reusable Vue components
│   │   ├── ChatInterface.vue       # Main chat interface
│   │   ├── ConversationList.vue   # Conversation list
│   │   ├── ConversationItem.vue   # Individual conversation item
│   │   ├── Login.vue               # Login screen
│   │   ├── Register.vue            # Registration screen
│   │   ├── Settings.vue            # User settings
│   │   ├── TwoFactorAuth.vue       # 2FA authentication
│   │   └── TwoFactorSettings.vue   # 2FA settings
│   ├── services/           # API services and utilities
│   │   ├── api.js                  # HTTP client for RAG API
│   │   ├── auth.js                 # Authentication management (localStorage)
│   │   └── orionUsers.js           # Orion Users service (login, register, 2FA)
│   ├── stores/             # Pinia stores (state management)
│   │   └── auth.js                 # Authentication store
│   ├── router/             # Route configuration
│   │   └── index.js                # Route definitions and guards
│   ├── App.vue             # Root application component
│   ├── main.js             # Application entry point
│   └── style.css           # Global styles
├── index.html              # Main HTML template
├── vite.config.js          # Vite configuration
├── package.json            # Dependencies and scripts
└── .env                    # Environment variables (create manually)
```

### Main Components

#### ChatInterface.vue
Main chat interface that manages:
- Sending and receiving messages
- API response streaming
- Markdown and code rendering
- Conversation management

#### Login.vue / Register.vue
User authentication with support for:
- Traditional login (email/password)
- Social login with Google (requires `VITE_GOOGLE_CLIENT_ID`)
- Two-factor authentication (2FA)

#### ConversationList.vue
Lists and manages conversations:
- View previous conversations
- Create new conversations
- Navigate between conversations

## 🔐 Authentication

The frontend supports multiple authentication methods:

### 1. Traditional Login
- Email and password
- Password validation (minimum 8 characters, uppercase, number, special character)
- Two-factor authentication (2FA) support

### 2. Social Login (Google)
- Authentication via Google Identity Services
- Requires `VITE_GOOGLE_CLIENT_ID` configuration
- Integration with Google One Tap and custom button

### 3. Two-Factor Authentication (2FA)
- Configurable per user
- Based on TOTP (Google Authenticator, Authy, etc.)
- Can be required for basic and/or social login

### Authentication Flow

```text
User accesses /login
    ↓
Chooses method: Traditional or Google
    ↓
Authenticates on backend (Orion Users)
    ↓
Receives JWT token
    ↓
Token stored (localStorage + Pinia store)
    ↓
Route guards verify authentication
    ↓
Access granted to /conversations or /chat
```

## 🌐 Routes

Main application routes:

- `/login` - Login screen (public)
- `/register` - Registration screen (public)
- `/conversations` - Conversation list (protected)
- `/chat` - Chat interface (protected)
- `/settings` - User settings (protected)

**Authentication Guards:** Protected routes require valid authentication. Unauthenticated users are redirected to `/login`.

## 🔌 API Integration

### RAG API (`/ai/*`)
Main chat and RAG processing service:
- `POST /ai/chatbot` - Chat with streaming
- `GET /ai/memory` - Retrieve history
- `POST /ai/chat` - Send message

### Orion Users (`/users/*`)
Authentication and user management service:
- `POST /users/create` - Create user
- `POST /users/login` - Traditional login
- `POST /users/login/google` - Google login
- `POST /users/login/2fa` - 2FA login
- `POST /users/2fa/settings` - Configure 2FA

## 🐛 Troubleshooting

### Google Login Button Does Not Appear

**Problem:** The "Sign in with Google" button is not visible or does not work.

**Solutions:**
1. Verify the `.env` file exists in the frontend directory root
2. Confirm `VITE_GOOGLE_CLIENT_ID` is correctly configured (not empty)
3. **If using Quarkus dev mode:**
   - Environment variables are embedded in the build
   - You MUST run `npm run build` in the `frontend` directory BEFORE running Quarkus
   - If you modified `.env`, do a new build
4. **If using Vite dev server (`npm run dev`):**
   - Restart the development server after creating/modifying `.env`
5. Check the browser console for Google Identity Services initialization errors
6. Confirm the Google script is loaded in `index.html`:
   ```html
   <script src="https://accounts.google.com/gsi/client" async defer></script>
   ```

### Error: "Google Client ID not configured"

**Cause:** The `VITE_GOOGLE_CLIENT_ID` variable is not defined or is an empty string.

**Solution:** Add the variable to the `.env` file and restart the server.

### Error: "Google Identity Services not loaded"

**Cause:** The Google script was not loaded or there are connection issues.

**Solutions:**
1. Check your internet connection
2. Confirm there are no ad blockers blocking the script
3. Check the browser console for CORS or loading errors

### Environment Variables Do Not Work

**Cause:** Variables do not start with `VITE_` or the server was not restarted.

**Solutions:**
1. Ensure all variables start with `VITE_`
2. Restart the development server (`Ctrl+C` and `npm run dev`)
3. Clear browser cache if necessary

### CORS Error

**Cause:** The backend is not configured to accept requests from the frontend.

**Solution:** Configure CORS in the backend to accept requests from `http://localhost:5173`.

### Build Does Not Generate Files

**Cause:** Compilation errors or permission issues.

**Solutions:**
1. Check for errors in the terminal during build
2. Run `npm install` again to ensure correct dependencies
3. Check write permissions on the output directory

## 📚 Additional Resources

- [Vue.js Documentation](https://vuejs.org/)
- [Vuetify Documentation](https://vuetifyjs.com/)
- [Vite Documentation](https://vitejs.dev/)
- [Google Identity Services](https://developers.google.com/identity/gsi/web)

## 🤝 Contributing

1. Fork the project
2. Create a branch for your feature (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -m 'Add new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Open a Pull Request

## 📄 License

This project contains confidential and proprietary information.
Unauthorized copying, distribution, or use of this file or its contents is strictly prohibited.

© 2025 Rodrigo Prestes Machado. All rights reserved.

