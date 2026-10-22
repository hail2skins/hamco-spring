import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { AdminNotesPage, AdminSlogansPage, AdminApiKeysPage, adminRequest } from './index';

const note = { id: 7, title: 'Existing', slug: 'existing', content: 'Body', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' };
const slogan = { id: 4, text: 'Hello', isActive: true, createdAt: '2026-01-01T00:00:00Z' };
const key = { id: 'key-1', name: 'Bot', keyPrefix: 'hamco_sk', isActive: true, isAdmin: false, createdAt: '2026-01-01T00:00:00Z' };
const fetchMock = vi.fn();

// Fetch is entirely local to these tests; the live site must never receive a write.
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
const response = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body, text: async () => JSON.stringify(body) });

// Admin links intentionally use client-side navigation so a click does not
// reload the page and discard the memory-only JWT. Supply the router context
// even when testing an individual page rather than the complete App.
function renderAdmin(page: ReactNode) {
  const view = render(<MemoryRouter>{page}</MemoryRouter>);
  return { ...view, rerender: (next: ReactNode) => view.rerender(<MemoryRouter>{next}</MemoryRouter>) };
}

test('notes list links to Razor-shaped create/edit/delete pages; create sends only editable fields', async () => {
  fetchMock.mockResolvedValueOnce(response([note])).mockResolvedValueOnce(response(note, 201));
  const user = userEvent.setup();
  const { rerender } = renderAdmin(<AdminNotesPage mode="index" token="jwt" />);
  expect(await screen.findByText('Existing')).toBeVisible();
  expect(screen.getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/admin/notes/edit/7');
  expect(screen.getByRole('link', { name: 'Delete' })).toHaveAttribute('href', '/admin/notes/delete/7');
  rerender(<AdminNotesPage mode="create" token="jwt" />);
  await user.type(screen.getByRole('textbox', { name: 'Title' }), 'New');
  await user.type(screen.getByRole('textbox', { name: 'Content (Markdown)' }), 'Content');
  await user.click(screen.getByRole('button', { name: 'Save' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/notes', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ Authorization: 'Bearer jwt' }), body: JSON.stringify({ title: 'New', content: 'Content' }) })));
  expect(screen.getByText(/Slug is generated/)).toBeVisible();
});

test('note edit loads detail and PUTs fields; delete requires explicit confirmation before DELETE', async () => {
  fetchMock.mockResolvedValueOnce(response(note)).mockResolvedValueOnce(response(note)).mockResolvedValueOnce(response(note)).mockResolvedValueOnce(response(null, 204));
  const user = userEvent.setup();
  const { rerender } = renderAdmin(<AdminNotesPage mode="edit" id="7" token="jwt" />);
  await user.clear(await screen.findByRole('textbox', { name: 'Title' }));
  await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Changed');
  await user.click(screen.getByRole('button', { name: 'Save Changes' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/notes/7', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ title: 'Changed', content: 'Body' }) })));
  rerender(<AdminNotesPage mode="delete" id="7" token="jwt" />);
  expect(await screen.findByText('Are you sure you want to delete this note?')).toBeVisible();
  expect(fetchMock.mock.calls.filter(([url, options]) => url === '/api/notes/7' && options?.method === 'DELETE')).toHaveLength(0);
  await user.click(screen.getByRole('button', { name: 'Delete Note' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/notes/7', expect.objectContaining({ method: 'DELETE' })));
});

test('slogan toggle uses a partial PUT and preserves text; edit and delete use confirmation', async () => {
  fetchMock.mockResolvedValueOnce(response([slogan])).mockResolvedValueOnce(response({ ...slogan, isActive: false })).mockResolvedValueOnce(response([slogan])).mockResolvedValueOnce(response(null, 204));
  const user = userEvent.setup();
  const { rerender } = renderAdmin(<AdminSlogansPage mode="index" token="jwt" />);
  await user.click(await screen.findByRole('checkbox', { name: 'Active' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/slogans/4', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ isActive: false }) })));
  rerender(<AdminSlogansPage mode="delete" id="4" token="jwt" />);
  expect(await screen.findByText('Are you sure you want to delete this slogan?')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Delete Slogan' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/slogans/4', expect.objectContaining({ method: 'DELETE' })));
});

test('slogan create and edit send text and active state', async () => {
  fetchMock.mockResolvedValueOnce(response(slogan, 201)).mockResolvedValueOnce(response([slogan])).mockResolvedValueOnce(response(slogan));
  const user = userEvent.setup();
  const { rerender } = renderAdmin(<AdminSlogansPage mode="create" token="jwt" />);
  await user.type(screen.getByRole('textbox', { name: 'Content' }), 'Hi');
  await user.click(screen.getByRole('button', { name: 'Save' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/slogans', expect.objectContaining({ method: 'POST', body: JSON.stringify({ text: 'Hi', isActive: true }) })));
  rerender(<AdminSlogansPage mode="edit" id="4" token="jwt" />);
  await user.clear(await screen.findByRole('textbox', { name: 'Content' }));
  await user.type(screen.getByRole('textbox', { name: 'Content' }), 'Edited');
  await user.click(screen.getByRole('button', { name: 'Save Changes' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/slogans/4', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ text: 'Edited', isActive: true }) })));
});

test('API key creation displays secret once in memory; list and show never reconstruct it', async () => {
  fetchMock.mockResolvedValueOnce(response({ id: 'key-2', name: 'New', key: 'secret-once', createdAt: '2026-01-01T00:00:00Z' }, 201)).mockResolvedValueOnce(response([key])).mockResolvedValueOnce(response(key));
  const user = userEvent.setup();
  const { rerender } = renderAdmin(<AdminApiKeysPage mode="create" token="jwt" />);
  await user.type(screen.getByRole('textbox', { name: 'Key Name' }), 'New');
  await user.click(screen.getByRole('button', { name: 'Generate API Key' }));
  expect(await screen.findByDisplayValue('secret-once')).toBeVisible();
  expect(fetchMock).toHaveBeenCalledWith('/api/admin/api-keys', expect.objectContaining({ method: 'POST', body: JSON.stringify({ name: 'New', isAdmin: false }), headers: expect.objectContaining({ Authorization: 'Bearer jwt' }) }));
  rerender(<AdminApiKeysPage mode="index" token="jwt" />);
  expect(await screen.findByText('Bot')).toBeVisible();
  expect(screen.queryByDisplayValue('secret-once')).not.toBeInTheDocument();
  rerender(<AdminApiKeysPage mode="show" id="key-1" token="jwt" />);
  expect(await screen.findByText('Bot')).toBeVisible();
  expect(screen.queryByDisplayValue('secret-once')).not.toBeInTheDocument();
});

test('API keys only allow revoke (soft delete), not permanent deletion; confirm before request', async () => {
  fetchMock.mockResolvedValueOnce(response([key])).mockResolvedValueOnce(response(null, 204));
  const user = userEvent.setup();
  renderAdmin(<AdminApiKeysPage mode="index" token="jwt" />);
  const row = (await screen.findByText('Bot')).closest('tr')!;
  expect(within(row).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  await user.click(within(row).getByRole('button', { name: 'Revoke' }));
  expect(fetchMock).toHaveBeenCalledTimes(1);
  await user.click(screen.getByRole('button', { name: 'Confirm Revoke' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/admin/api-keys/key-1', expect.objectContaining({ method: 'DELETE', headers: expect.objectContaining({ Authorization: 'Bearer jwt' }) })));
});

test('note Markdown editor embeds a pasted image at the caret as in Razor', async () => {
  renderAdmin(<AdminNotesPage mode="create" token="jwt" />);
  const textarea = screen.getByRole('textbox', { name: 'Content (Markdown)' }) as HTMLTextAreaElement;
  fireEvent.change(textarea, { target: { value: 'hello world' } });
  textarea.setSelectionRange(6, 6);
  fireEvent.paste(textarea, { clipboardData: { items: [{ type: 'image/png', getAsFile: () => new File(['png'], 'image.png', { type: 'image/png' }) }] } });
  await waitFor(() => expect(textarea.value).toMatch(/hello \n!\[pasted image\]\(data:image\/png;base64,/));
  expect(textarea.value).toContain('world');
});

test('an absent bearer token blocks a request before calling fetch', async () => {
  // Browser cookies cannot authorize admin writes in the Spring contract.
  await expect(adminRequest('/api/admin/api-keys', '')).rejects.toThrow('Admin sign-in is required.');
  expect(fetchMock).not.toHaveBeenCalled();
});

test('create cannot issue two secrets when submit is clicked twice during one request', async () => {
  let finish!: (value: ReturnType<typeof response>) => void;
  fetchMock.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  const user = userEvent.setup();
  renderAdmin(<AdminApiKeysPage mode="create" token="jwt" />);
  await user.type(screen.getByRole('textbox', { name: 'Key Name' }), 'One');
  await user.click(screen.getByRole('button', { name: 'Generate API Key' }));
  await user.click(screen.getByRole('button', { name: 'Generate API Key' }));
  expect(fetchMock).toHaveBeenCalledTimes(1);
  finish(response({ id: '2', name: 'One', key: 'only-one', createdAt: '2026-01-01T00:00:00Z' }, 201));
  expect(await screen.findByDisplayValue('only-one')).toBeVisible();
});
