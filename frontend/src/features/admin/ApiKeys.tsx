import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { adminRequest, shortDate, type ApiKey, type IssuedKey } from './api';

export type ApiKeyMode = 'index' | 'create' | 'show' | 'delete';
export interface ApiKeysPageProps { mode: ApiKeyMode; id?: string; token: string; onSaved?: () => void }

export function AdminApiKeysPage({ mode, id, token, onSaved }: ApiKeysPageProps) {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [detail, setDetail] = useState<ApiKey | null>(null);
  const [issued, setIssued] = useState<IssuedKey | null>(null);
  const [name, setName] = useState('');
  const [admin, setAdmin] = useState(false);
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let current = true;
    setIssued(null); setError(''); setDone(false); setPending(null); setDetail(null);
    if (mode === 'create') { setName(''); setAdmin(false); return; }
    if (mode !== 'index' && !id) { setError('API key ID is required.'); return; }
    adminRequest<ApiKey[] | ApiKey>(mode === 'index' ? '/api/admin/api-keys' : `/api/admin/api-keys/${encodeURIComponent(id!)}`, token)
      .then(data => { if (current) { if (Array.isArray(data)) setKeys(data); else setDetail(data); } })
      .catch(e => { if (current) setError(e.message); });
    return () => { current = false; };
  }, [mode, id, token]);

  async function create(event: FormEvent) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const result = await adminRequest<IssuedKey>('/api/admin/api-keys', token, 'POST', { name: name.trim(), isAdmin: admin });
      // Secret exists only in component memory. Navigating away unmounts this
      // view; list/detail APIs return metadata and cannot recover the secret.
      setIssued(result); onSaved?.();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  async function revoke(target: string) {
    setError('');
    try {
      await adminRequest(`/api/admin/api-keys/${encodeURIComponent(target)}`, token, 'DELETE');
      setKeys(previous => previous.map(k => k.id === target ? { ...k, isActive: false } : k));
      if (detail?.id === target) setDetail({ ...detail, isActive: false });
      setPending(null); setDone(true); onSaved?.();
    } catch (e) { setError((e as Error).message); }
  }
  const confirmation = pending && <div className="alert alert-warning" role="alert"><p>Are you sure you want to revoke this API key? It will stop working immediately. Revoked keys remain in the list for audit; permanent deletion is not available.</p><button type="button" className="btn btn-warning" onClick={() => void revoke(pending)}>Confirm Revoke</button> <button type="button" className="btn btn-secondary" onClick={() => setPending(null)}>Cancel</button></div>;
  return <section className="row gx-4 gx-lg-5 justify-content-center"><div className="col-md-10 col-lg-10 col-xl-8">
    {error && <div role="alert" className="alert alert-danger">{error}</div>}
    {done && <div role="status" className="alert alert-success">API key revoked.</div>}
    {mode === 'index' && <><div className="d-flex justify-content-between align-items-center mb-4"><h2>Manage API Keys</h2><Link className="btn btn-primary" to="/admin/apikeys/create">Create New API Key</Link></div>{keys.length ? <><div className="table-responsive"><table className="table table-hover"><thead><tr><th>Name</th><th>Prefix</th><th>Role</th><th>Status</th><th>Created</th><th>Actions</th></tr></thead><tbody>{keys.map(k => <tr key={k.id} className={k.isActive ? '' : 'table-secondary'}><td><Link to={`/admin/apikeys/show/${encodeURIComponent(k.id)}`}>{k.name}</Link></td><td><code>{k.keyPrefix}</code></td><td><span className={`badge ${k.isAdmin ? 'bg-danger' : 'bg-info'}`}>{k.isAdmin ? 'Admin' : 'Read-Only'}</span></td><td><span className={`badge ${k.isActive ? 'bg-success' : 'bg-secondary'}`}>{k.isActive ? 'Active' : 'Revoked'}</span></td><td>{shortDate(k.createdAt)}</td><td>{k.isActive && <button type="button" className="btn btn-sm btn-warning" onClick={() => setPending(k.id)}>Revoke</button>}</td></tr>)}</tbody></table></div>{confirmation}<div className="alert alert-info mt-4"><h5 className="alert-heading">About API Keys</h5><ul className="mb-0"><li>Admin keys can create, edit, and delete notes</li><li>Read-Only keys can only read notes</li><li>API keys are shown only once when created</li><li>Revoked keys remain in the list for audit; permanent deletion is not supported.</li></ul></div></> : !error && <div className="alert alert-info"><p className="mb-0">No API keys found. <Link to="/admin/apikeys/create">Create your first API key</Link> to allow external services to access the API.</p></div>}</>}
    {mode === 'create' && (issued ? <><h2 className="mb-4">🔐 Save Your API Key</h2><div className="alert alert-danger"><strong>This is the only time you'll see this key!</strong> Copy it now and store it securely. If you lose it, you'll need to create a new one.</div><div className="card border-primary mb-4"><div className="card-header bg-primary text-white"><strong>{issued.name}</strong></div><div className="card-body"><label htmlFor="issued-key" className="form-label fw-bold">Your API Key:</label><div className="input-group"><input id="issued-key" type="text" className="form-control font-monospace" value={issued.key} readOnly /><button type="button" className="btn btn-outline-secondary" onClick={() => void navigator.clipboard?.writeText(issued.key)}>Copy</button></div><div className="alert alert-light border"><small className="text-muted">Key ID: {issued.id}<br />Created: {shortDate(issued.createdAt)}</small></div></div></div><div className="card mb-4"><div className="card-header">Usage Example</div><div className="card-body">Use this key in the <code>X-API-Key</code> header. Never commit it to source control.</div></div><Link to="/admin/apikeys" className="btn btn-primary">I've Saved the Key - Go to API Keys List</Link></> : <><h2 className="mb-4">Create New API Key</h2><div className="alert alert-warning"><strong>Important:</strong> The API key will be shown <strong>only once</strong> after creation. Copy and save it securely.</div><form onSubmit={create}><div className="mb-3"><label htmlFor="key-name" className="form-label">Key Name</label><input id="key-name" className="form-control" required maxLength={255} placeholder="e.g., Skippy Bot, Production Deploy, Personal Scripts" value={name} onChange={e => setName(e.target.value)} /><div className="form-text">A descriptive name to help you remember what this key is for.</div></div><div className="mb-3"><div className="form-check"><input id="key-admin" className="form-check-input" type="checkbox" checked={admin} onChange={e => setAdmin(e.target.checked)} /><label htmlFor="key-admin" className="form-check-label">Grant Admin Access</label></div><div className="form-text">Admin keys can create, edit, and delete notes. Read-only keys can only view notes.</div></div><div className="d-flex gap-2"><button className="btn btn-primary" disabled={busy}>Generate API Key</button><Link to="/admin/apikeys" className="btn btn-secondary">Cancel</Link></div></form></>)}
    {(mode === 'show' || mode === 'delete') && <><h2>API Key Details</h2>{detail && <div className="card mb-4"><div className="card-body"><dl className="row"><dt className="col-sm-3">Name:</dt><dd className="col-sm-9">{detail.name}</dd><dt className="col-sm-3">Prefix:</dt><dd className="col-sm-9"><code>{detail.keyPrefix}</code></dd><dt className="col-sm-3">Status:</dt><dd className="col-sm-9">{detail.isActive ? 'Active' : 'Revoked'}</dd><dt className="col-sm-3">Created:</dt><dd className="col-sm-9">{shortDate(detail.createdAt)}</dd></dl><p>The full API key cannot be displayed again.</p>{detail.isActive && <button type="button" className="btn btn-warning" onClick={() => setPending(detail.id)}>Revoke</button>}</div></div>}{confirmation}<Link to="/admin/apikeys" className="btn btn-secondary">Back to API Keys</Link>{mode === 'delete' && <p>Permanent deletion is not supported by the API. Revoke the key instead; its audit record remains.</p>}</>}
  </div></section>;
}
