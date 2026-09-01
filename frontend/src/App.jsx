import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { SessionProvider, useSession } from './lib/session.jsx';
import { ThemeProvider } from './ui/theme.jsx';
import { ToastProvider } from './ui/kit.jsx';
import Shell from './ui/Shell.jsx';
import SignIn from './pages/SignIn.jsx';
import Overview from './pages/Overview.jsx';
import Customers from './pages/Customers.jsx';
import Banks from './pages/Banks.jsx';
import PrivacyLayer from './pages/PrivacyLayer.jsx';
import RiskSimulator from './pages/RiskSimulator.jsx';
import Operators from './pages/Operators.jsx';
import Account from './pages/Account.jsx';

function Routed() {
  const { status, isSuperAdmin } = useSession();

  if (status === 'loading') {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <span className="spin" />
      </div>
    );
  }

  if (status === 'anonymous') {
    return (
      <Routes>
        <Route path="*" element={<SignIn />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Overview />} />
        <Route path="customers" element={<Customers />} />
        <Route path="banks" element={<Banks />} />
        <Route path="privacy" element={<PrivacyLayer />} />
        <Route path="risk" element={<RiskSimulator />} />
        <Route path="operators" element={isSuperAdmin ? <Operators /> : <Navigate to="/" replace />} />
        <Route path="account" element={<Account />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
        <SessionProvider>
          <ToastProvider>
            <Routed />
          </ToastProvider>
        </SessionProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
