import React from 'react';
import { createRoot } from 'react-dom/client';

import App from './App';
import { AuthProvider } from './context/auth/AuthProvider';
import { NotificationProvider } from './context/notification/NotificationProvider';
import ThemeProvider from './context/theme/ThemeProvider';

import './index.scss';

// Nothing to bootstrap for auth here any more.
//
// Amplify required its token storage to be installed BEFORE `Amplify.configure()`,
// because configure() could immediately process an OAuth callback and would read
// the PKCE verifier and state from whatever storage was active at that instant.
// That ordering constraint — and the CookieStorage block that satisfied it — is
// gone: oidc-client-ts creates its UserManager lazily on first use
// (services/keycloak.ts) and the code exchange is an explicit step on the
// /authCallback route.

const container = document.getElementById('root');
if (!container) throw new Error('Root container #root not found');

createRoot(container).render(
  <React.StrictMode>
    <AuthProvider>
      <ThemeProvider>
        <NotificationProvider>
          <App />
        </NotificationProvider>
      </ThemeProvider>
    </AuthProvider>
  </React.StrictMode>,
);
