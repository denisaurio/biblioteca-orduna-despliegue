import { AuthProvider } from './context/AuthContext.jsx';
import { RealtimeProvider } from './context/RealtimeContext.jsx';
import { ToastProvider } from './context/ToastContext.jsx';
import AppRoutes from './routes/AppRoutes.jsx';

function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <RealtimeProvider>
          <AppRoutes />
        </RealtimeProvider>
      </AuthProvider>
    </ToastProvider>
  );
}

export default App;
