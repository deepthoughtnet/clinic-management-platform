import { Link, NavLink, Route, Routes, useLocation } from "react-router-dom";
import {
  AivaAgentsPage,
  AivaAnalyticsPage,
  AivaArchitecturePage,
  AivaDemoPage,
  AivaIndustriesPage,
  AivaLandingPage,
  AivaPlatformPage,
  AivaRoadmapPage,
} from "./pages/aiva/AivaPages";

const runtimeHref = import.meta.env.VITE_CAREAI_RUNTIME_URL?.trim() || "http://localhost:5175/patient/careai";

const navItems = [
  { to: "/", label: "Overview" },
  { to: "/demo", label: "Demo" },
  { to: "/agents", label: "Agents" },
  { to: "/platform", label: "Platform" },
  { to: "/industries", label: "Industries" },
  { to: "/architecture", label: "Architecture" },
  { to: "/roadmap", label: "Roadmap" },
  { to: "/analytics", label: "Analytics" },
];

function AppShell() {
  const location = useLocation();

  return (
    <div className="site-shell">
      <header className="site-header aiva-header">
        <Link to="/" className="brand aiva-brand">
          <span className="brand-badge aiva-brand-badge">AI</span>
          <span className="brand-meta">
            <strong>AIVA</strong>
            <small>AI Voice Intelligence &amp; Agentic Workflow Platform</small>
          </span>
        </Link>
        <nav className="main-nav" aria-label="AIVA navigation">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-link${isActive || location.pathname === item.to ? " is-active" : ""}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="header-actions">
          <Link className="secondary-button" to="/architecture">
            View architecture
          </Link>
          <a className="primary-button" href={runtimeHref}>
            Talk to AIVA
          </a>
        </div>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<AivaLandingPage runtimeHref={runtimeHref} />} />
          <Route path="/demo" element={<AivaDemoPage runtimeHref={runtimeHref} />} />
          <Route path="/agents" element={<AivaAgentsPage />} />
          <Route path="/platform" element={<AivaPlatformPage />} />
          <Route path="/industries" element={<AivaIndustriesPage />} />
          <Route path="/architecture" element={<AivaArchitecturePage />} />
          <Route path="/roadmap" element={<AivaRoadmapPage />} />
          <Route path="/analytics" element={<AivaAnalyticsPage />} />
        </Routes>
      </main>
      <footer className="site-footer aiva-footer">
        <div className="footer-grid">
          <section className="footer-brand-block">
            <span className="eyebrow">AIVA</span>
            <strong>AIVA</strong>
            <p className="footer-tagline">AI Voice Intelligence &amp; Agentic Workflow Platform</p>
            <p>Talk. Understand. Act.</p>
            <div className="footer-meaning">
              <p>AIVA v1 is the product layer on top of the AIVA Runtime.</p>
              <p>No backend runtime duplication, no real data exposure.</p>
            </div>
          </section>
          <section className="footer-column">
            <strong>Product</strong>
            <div className="footer-link-list">
              <Link to="/">Overview</Link>
              <Link to="/demo">Demo</Link>
              <Link to="/agents">Agents</Link>
              <Link to="/platform">Platform</Link>
              <Link to="/industries">Industries</Link>
              <Link to="/architecture">Architecture</Link>
              <Link to="/roadmap">Roadmap</Link>
              <Link to="/analytics">Analytics</Link>
            </div>
          </section>
          <section className="footer-column">
            <strong>Runtime</strong>
            <div className="footer-link-list">
              <a href={runtimeHref}>Live AIVA runtime</a>
              <span>STT / LLM / TTS / Workflow engine</span>
            </div>
          </section>
          <section className="footer-column">
            <strong>Safety</strong>
            <div className="footer-link-list">
              <span>Demo-safe prompts only</span>
              <span>No secrets or API keys exposed</span>
              <span>Reusable product layer only</span>
            </div>
          </section>
        </div>
        <div className="footer-bottom">
          <p>© 2026 AIVA — AI Voice Intelligence &amp; Agentic Workflow Platform</p>
          <a href={runtimeHref}>Open live AIVA runtime</a>
        </div>
      </footer>
    </div>
  );
}

export function App() {
  return <AppShell />;
}
