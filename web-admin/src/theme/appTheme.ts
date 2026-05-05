import { alpha, createTheme } from "@mui/material/styles";

export const appTheme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#0f766e",
      light: "#14b8a6",
      dark: "#115e59",
      contrastText: "#ffffff",
    },
    secondary: {
      main: "#d97706",
      light: "#f59e0b",
      dark: "#b45309",
      contrastText: "#ffffff",
    },
    background: {
      default: "#f6fbfa",
      paper: "#ffffff",
    },
    text: {
      primary: "#0f172a",
      secondary: "#475569",
    },
    divider: alpha("#0f172a", 0.08),
  },
  shape: { borderRadius: 14 },
  typography: {
    fontFamily: 'Inter, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    fontSize: 13,
    h4: { fontWeight: 900, letterSpacing: -0.4 },
    h5: { fontWeight: 900, letterSpacing: -0.3 },
    h6: { fontWeight: 800, letterSpacing: -0.2 },
    subtitle1: { fontWeight: 700 },
    body2: { lineHeight: 1.5 },
    button: { fontWeight: 700 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          background:
            "radial-gradient(circle at top left, rgba(15,118,110,0.06), transparent 40%), linear-gradient(180deg, rgba(246,251,250,1) 0%, rgba(255,255,255,1) 200px)",
        },
      },
    },
    MuiButton: {
      defaultProps: { variant: "contained", disableElevation: true },
      styleOverrides: {
        root: { textTransform: "none", borderRadius: 12, boxShadow: "none" },
      },
    },
    MuiPaper: {
      styleOverrides: {
        rounded: { borderRadius: 16 },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 18,
          boxShadow: "0 10px 30px rgba(15, 23, 42, 0.06)",
          border: `1px solid ${alpha("#0f172a", 0.05)}`,
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundImage: "none",
          backgroundColor: "#ffffff",
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundImage: "none",
          boxShadow: "none",
        },
      },
    },
  },
});
