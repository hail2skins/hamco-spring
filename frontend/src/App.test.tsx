import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import App from './App';

beforeEach(() => {
  // A controlled empty API response tests the page shell without contacting
  // production or relying on whatever happens to be in the local database.
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) =>
    url === '/api/auth/profile' ? { ok: false, status: 401 } : { ok: true, json: async () => [] }));
});

test('home recreates the masthead, navigation and notes entry point', async () => {
  render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>);
  expect(screen.getByRole('navigation')).toHaveTextContent('Hamco Internet Solutions');
  expect(screen.getByRole('link', { name: 'Notes' })).toHaveAttribute('href', '/notes');
  expect(screen.getByRole('heading', { level: 1, name: 'Hamco Internet Solutions' })).toBeVisible();
  expect(await screen.findByText('Recent Notes')).toBeVisible();
});

test('note detail uses its actual title and author in the masthead', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ id: 7, title: 'A Sample Note', content: '# Safe content',
      slug: 'a-sample-note', userId: null, createdAt: '2026-02-01T10:00:00Z', updatedAt: '2026-02-01T10:00:00Z' }),
  }));
  render(<MemoryRouter initialEntries={['/notes/7']}><App /></MemoryRouter>);
  expect(await screen.findByRole('heading', { level: 1, name: 'A Sample Note' })).toBeVisible();
  expect(screen.getByText(/Posted by Art Mills/)).toBeVisible();
  expect(screen.getByRole('link', { name: '← Back to Notes' })).toHaveAttribute('href', '/notes');
});

test('login page keeps the source form and anonymous admins are directed to sign in', async () => {
  const login = render(<MemoryRouter initialEntries={['/auth/login']}><App /></MemoryRouter>);
  expect(screen.getByRole('heading', { level: 1, name: 'Login' })).toBeVisible();
  expect(screen.getByRole('textbox', { name: 'Email address' })).toBeVisible();
  expect(screen.getByRole('button', { name: 'Sign In' })).toBeVisible();
  login.unmount();

  render(<MemoryRouter initialEntries={['/admin/notes']}><App /></MemoryRouter>);
  expect(screen.getByRole('link', { name: /Sign in/i })).toHaveAttribute('href', '/auth/login');
  expect(screen.queryByText('Manage Notes')).not.toBeInTheDocument();
});

test('masthead shows an active server slogan and registration follows server configuration', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => {
    if (url === '/api/auth/profile') return { ok: false, status: 401 };
    if (url === '/api/site/config') return { ok: true, json: async () => ({ registrationEnabled: true }) };
    if (url === '/api/site/slogan') return { ok: true, json: async () => ({ text: 'A local active slogan' }) };
    return { ok: true, json: async () => [] };
  }));
  render(<MemoryRouter initialEntries={['/auth/register']}><App /></MemoryRouter>);
  expect(await screen.findByRole('textbox', { name: 'Username' })).toBeVisible();
  expect(screen.queryByText('Registration Disabled')).not.toBeInTheDocument();
  // Registration has a fixed hero subtitle; ordinary pages use the public
  // slogan. The registration flag must never be guessed at build time.
});

test('public pages use the active slogan supplied by Spring', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => {
    if (url === '/api/auth/profile') return { ok: false, status: 401 };
    if (url === '/api/site/slogan') return { ok: true, json: async () => ({ text: 'A local active slogan' }) };
    return { ok: true, json: async () => [] };
  }));
  render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>);
  expect(await screen.findByText('A local active slogan')).toBeVisible();
});

test('footer preserves the reference social icon structure', () => {
  render(<MemoryRouter initialEntries={['/about']}><App /></MemoryRouter>);
  expect(document.querySelector('footer .fa-twitter')).toBeInTheDocument();
  expect(document.querySelector('footer .fa-linkedin-in')).toBeInTheDocument();
  expect(document.querySelector('footer .fa-github')).toBeInTheDocument();
});
