import React from "react";
import ReactDOM from "react-dom/client";
import { ApolloProvider } from "@apollo/client/react";
import { CssBaseline, ThemeProvider } from "@mui/material";

import { apolloClient } from "./api/graphqlClient";
import { appTheme } from "./theme/appTheme";
import App from "./App";
import AuthProvider from "./auth/AuthProvider";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AuthProvider>
      <ApolloProvider client={apolloClient}>
        <ThemeProvider theme={appTheme}>
          <CssBaseline />
          <App />
        </ThemeProvider>
      </ApolloProvider>
    </AuthProvider>
  </React.StrictMode>
);
