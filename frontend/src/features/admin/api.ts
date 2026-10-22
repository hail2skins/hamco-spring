export interface Note { id: number; title: string; slug: string; content: string; createdAt: string; updatedAt: string | null }
export interface Slogan { id: number; text: string; isActive: boolean; createdAt: string; updatedAt?: string | null }
export interface ApiKey { id: string; name: string; keyPrefix: string; isAdmin: boolean; isActive: boolean; createdAt: string; expiresAt?: string | null }
export interface IssuedKey { id: string; name: string; key: string; createdAt: string }
export type Mode = 'index' | 'create' | 'edit' | 'delete';
export interface PageProps { mode: Mode; id?: string; token: string; onSaved?: () => void }

// Cookies cannot authorize writes in the Spring API. Every protected request
// receives the caller's in-memory JWT; this module never persists credentials.
export async function adminRequest<T>(path: string, token: string, method = 'GET', body?: object): Promise<T> {
  if (!token) throw new Error('Admin sign-in is required.');
  const result = await fetch(path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!result.ok) {
    // Avoid reflecting server error bodies: an accidental secret in an error
    // must not be placed into the page or developer console.
    throw new Error(`Request failed (${result.status}).`);
  }
  return result.status === 204 ? undefined as T : result.json() as Promise<T>;
}

export function shortDate(value?: string | null): string {
  return value ? new Date(value).toLocaleDateString('en-US', { timeZone: 'UTC', month: '2-digit', day: '2-digit', year: 'numeric' }) : '—';
}
