import { createContext, useCallback, useContext, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

export type AuthUser = { userId: string; email: string; roles: string[]; expiresAt: string };
type Session = AuthUser & { token: string };
type AuthContextValue = {
  user: AuthUser | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  refreshProfile: () => Promise<void>;
  logout: () => Promise<void>;
  /** Only explicit bearer credentials authorize writes; cookies are for safe reads. */
  authenticatedFetch: (url: string, init: RequestInit) => Promise<Response>;
};
const AuthContext = createContext<AuthContextValue | null>(null);
const connectionError = 'Unable to connect to server. Please try again.';

// API errors are text, never HTML: the old MVC scripts interpolated server messages into innerHTML.
async function payload(response: Response): Promise<Record<string, unknown>> {
  try { return await response.json() as Record<string, unknown>; } catch { return {}; }
}
function message(data: Record<string, unknown>, fallback: string) {
  return typeof data.message === 'string' && data.message ? data.message : fallback;
}
async function post(path: string, body: object) {
  return fetch(`/api/auth/${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin', body: JSON.stringify(body),
  });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  // A reload intentionally drops the JWT. The HttpOnly cookie can still restore
  // read-only identity via refreshProfile, but never supplies write authority.
  const [session, setSession] = useState<Session | null>(null);
  const [cookieUser, setCookieUser] = useState<AuthUser | null>(null);
  const identityVersion = useRef(0);
  const login = useCallback(async (email: string, password: string) => {
    const result = await post('login', { email, password });
    const data = await payload(result);
    if (!result.ok) throw new Error(message(data, 'Login failed.'));
    if (typeof data.token !== 'string' || !data.token) throw new Error('Login failed.');
    const cookie = await post('cookie', { token: data.token });
    if (!cookie.ok) throw new Error('Login succeeded but cookie setup failed. Please try again.');
    // No localStorage/sessionStorage: the JWT remains in this React provider only.
    const next = data as unknown as Session;
    identityVersion.current++;
    setSession(next);
    setCookieUser(next);
  }, []);
  const refreshProfile = useCallback(async () => {
    const version = identityVersion.current;
    const result = await fetch('/api/auth/profile', { method: 'GET', credentials: 'same-origin' });
    // A pre-login 401 can finish after a successful cookie exchange. In that
    // case its old answer must not erase the new in-memory bearer credential.
    if (version !== identityVersion.current) return;
    if (result.ok) {
      const data = await payload(result);
      setCookieUser(data as AuthUser);
    } else {
      setCookieUser(null);
      setSession(null);
    }
  }, []);
  const logout = useCallback(async () => {
    // Clearing the local credential first prevents a slow logout from permitting writes.
    identityVersion.current++;
    setSession(null);
    setCookieUser(null);
    await fetch('/api/auth/logout', { method: 'GET', credentials: 'same-origin' });
  }, []);
  const authenticatedFetch = useCallback((url: string, init: RequestInit) => {
    // Never attach a bearer token to an external URL or protocol-relative host.
    if (!url.startsWith('/api/')) return Promise.reject(new Error('Authenticated requests must target this site’s API.'));
    const method = (init.method ?? 'GET').toUpperCase();
    if (['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      return fetch(url, { ...init, credentials: 'same-origin' });
    }
    if (!session?.token) return Promise.reject(new Error('Sign in again to make changes.'));
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${session.token}`);
    return fetch(url, { ...init, method, credentials: 'omit', headers });
  }, [session]);
  return <AuthContext.Provider value={{ user: session ?? cookieUser, token: session?.token ?? null, login, refreshProfile, logout, authenticatedFetch }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

function Frame({ children }: { children: ReactNode }) {
  // These are the original Bootstrap grid classes supplied by source.css.
  return <div className="row gx-4 gx-lg-5 justify-content-center"><div className="col-md-10 col-lg-10 col-xl-8">{children}</div></div>;
}
function Alert({ kind, children }: { kind: 'danger' | 'success' | 'warning' | 'info'; children: ReactNode }) {
  return <div className={`alert alert-${kind}`} role="alert">{children}</div>;
}

export function RegisterPage({ registrationEnabled = false }: { registrationEnabled?: boolean }) {
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const username = String(fields.get('username') ?? '');
    const email = String(fields.get('email') ?? '');
    const password = String(fields.get('password') ?? '');
    if (password !== fields.get('confirmPassword')) { setError('Passwords do not match'); return; }
    setError(''); setBusy(true);
    try {
      const result = await post('register', { username, email, password });
      const data = await payload(result);
      if (!result.ok) { setError(message(data, 'Registration failed. Please try again.')); return; }
      navigate(`/auth/verification-pending?email=${encodeURIComponent(typeof data.email === 'string' ? data.email : email)}`);
    } catch { setError('An error occurred. Please try again later.'); }
    finally { setBusy(false); }
  }
  return <Frame>{!registrationEnabled ? <Alert kind="warning"><h4 className="alert-heading">Registration Disabled</h4><p>Registration is currently disabled. Please contact the administrator if you need an account.</p><hr /><p className="mb-0"><a href="/" className="alert-link">Return to Home</a></p></Alert> :
    <div className="card shadow-sm"><div className="card-body p-4"><h3 className="card-title text-center mb-4">Create Account</h3>
      <form onSubmit={submit}>
        {(['username', 'email', 'password', 'confirmPassword'] as const).map(name => <div className="mb-3" key={name}><label className="form-label" htmlFor={`register-${name}`}>{({ username: 'Username', email: 'Email address', password: 'Password', confirmPassword: 'Confirm Password' })[name]}</label><input id={`register-${name}`} name={name} className="form-control" type={name === 'email' ? 'email' : name.toLowerCase().includes('password') ? 'password' : 'text'} minLength={name === 'password' ? 6 : undefined} required /></div>)}
        {error && <Alert kind="danger">{error}</Alert>}
        <div className="d-grid gap-2"><button className="btn btn-primary btn-lg" disabled={busy}>Register</button></div>
      </form><div className="text-center mt-3"><p>Already have an account? <a href="/auth/login">Login here</a></p></div>
    </div></div>}</Frame>;
}

export function VerificationPendingPage() {
  const [params] = useSearchParams();
  const email = params.get('email')?.trim();
  return <Frame><Alert kind="info"><h4>Check your inbox</h4><p>We sent a verification email{email ? ` to ${email}` : ''} with a link that expires in 20 minutes.</p><p className="mb-0">Once verified, you can log in.</p></Alert><div className="text-center mt-4"><a href="/auth/login" className="btn btn-primary">Go to Login</a></div></Frame>;
}

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token');
  const [error, setError] = useState(!token?.trim() ? 'Invalid or expired verification link.' : '');
  useEffect(() => {
    if (!token?.trim()) return;
    let active = true;
    // Fetch follows the backend's redirect to the SPA login route. Accept only
    // our own origin and exact login path, not an arbitrary server-provided URL.
    fetch(`/api/auth/verify-email?token=${encodeURIComponent(token)}`, { method: 'GET', credentials: 'same-origin' })
      .then(result => {
        if (!active) return;
        const target = new URL(result.url || '', window.location.origin);
        if (result.redirected && target.origin === window.location.origin && target.pathname === '/auth/login') {
          navigate(`/auth/login${target.search}`, { replace: true });
        } else setError('Invalid or expired verification link.');
      })
      .catch(() => { if (active) setError(connectionError); });
    return () => { active = false; };
  }, [token, navigate]);
  return <Frame>{error ? <Alert kind="danger">{error}</Alert> : <p>Verifying your email...</p>}</Frame>;
}

export function LoginPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuth();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const email = String(fields.get('email'));
    const password = String(fields.get('password'));
    // After copying the credential into this short-lived request, clear the
    // input so a failed attempt cannot expose it in a DOM snapshot or form.
    const passwordInput = event.currentTarget.elements.namedItem('password');
    if (passwordInput instanceof HTMLInputElement) passwordInput.value = '';
    setError(''); setBusy(true);
    try {
      await login(email, password);
      navigate('/');
    } catch (failure) { setError(failure instanceof TypeError ? connectionError : failure instanceof Error ? failure.message : connectionError); }
    finally { setBusy(false); }
  }
  return <Frame><p>Please sign in to your account.</p>
    {error ? <Alert kind="danger">{error}</Alert> : params.get('verified') === '1' ? <Alert kind="success">Email verified. You can log in now.</Alert> : params.has('error') ? <Alert kind="danger">Invalid or expired verification link.</Alert> : null}
    <form onSubmit={submit}><div className="form-floating mb-3"><input type="email" className="form-control" id="login-email" name="email" placeholder="name@example.com" required /><label htmlFor="login-email">Email address</label></div>
      <div className="form-floating mb-3"><input type="password" className="form-control" id="login-password" name="password" placeholder="Password" required /><label htmlFor="login-password">Password</label></div>
      <div className="d-grid gap-2 mb-3"><button className="btn btn-primary" disabled={busy}>Sign In{busy && <span className="spinner-border spinner-border-sm" role="status" aria-label="Signing in" />}</button></div>
      <div className="text-center"><a href="/auth/forgot-password" className="text-decoration-none">Forgot password?</a></div></form>
    <hr className="my-4" /><div className="text-center"><p className="text-muted">Don't have an account? <a href="/auth/register">Register here</a></p></div>
  </Frame>;
}

export function ForgotPasswordPage() {
  const [alert, setAlert] = useState<{ kind: 'danger' | 'success'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setAlert(null);
    const email = String(new FormData(event.currentTarget).get('email'));
    try {
      const result = await post('forgot-password', { email });
      const data = await payload(result);
      setAlert(result.ok ? { kind: 'success', text: message(data, 'If your email is registered, you will receive a password reset link shortly.') } : { kind: 'danger', text: message(data, 'Unable to send reset link.') });
    } catch { setAlert({ kind: 'danger', text: connectionError }); }
    finally { setBusy(false); }
  }
  return <Frame>{alert && <Alert kind={alert.kind}>{alert.text}</Alert>}
    <form onSubmit={submit}><div className="form-floating mb-3"><input type="email" className="form-control" id="forgot-email" name="email" required placeholder="name@example.com" /><label htmlFor="forgot-email">Email address</label></div><div className="d-grid gap-2"><button className="btn btn-primary" disabled={busy}>Send reset link</button></div></form>
    <div className="text-center mt-3"><a href="/auth/login">Back to login</a></div></Frame>;
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const navigate = useNavigate();
  const [alert, setAlert] = useState<{ kind: 'danger' | 'success'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (alert?.kind !== 'success') return;
    const timer = window.setTimeout(() => navigate('/auth/login'), 1200);
    return () => window.clearTimeout(timer);
  }, [alert, navigate]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token?.trim()) return;
    const fields = new FormData(event.currentTarget);
    const newPassword = String(fields.get('newPassword'));
    if (newPassword !== fields.get('confirmPassword')) { setAlert({ kind: 'danger', text: 'Passwords do not match.' }); return; }
    setBusy(true); setAlert(null);
    try {
      const result = await post('reset-password', { token, newPassword });
      const data = await payload(result);
      setAlert(result.ok ? { kind: 'success', text: 'Password reset successful. Redirecting to login...' } : { kind: 'danger', text: message(data, 'Unable to reset password.') });
    } catch { setAlert({ kind: 'danger', text: connectionError }); }
    finally { setBusy(false); }
  }
  return <Frame>{alert && <Alert kind={alert.kind}>{alert.text}</Alert>}
    {!token?.trim() ? <Alert kind="danger">Invalid reset token.</Alert> : <form onSubmit={submit}>
      <div className="form-floating mb-3"><input type="password" className="form-control" id="newPassword" name="newPassword" minLength={6} required placeholder="New password" /><label htmlFor="newPassword">New password</label></div>
      <div className="form-floating mb-3"><input type="password" className="form-control" id="confirmPassword" name="confirmPassword" minLength={6} required placeholder="Confirm password" /><label htmlFor="confirmPassword">Confirm password</label></div>
      <div className="d-grid gap-2"><button className="btn btn-primary" disabled={busy}>Reset Password</button></div></form>}
    <div className="text-center mt-3"><a href="/auth/login">Back to login</a></div></Frame>;
}
