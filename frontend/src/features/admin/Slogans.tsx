import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { adminRequest, shortDate, type Slogan, type PageProps } from './api';

export function AdminSlogansPage({ mode, id, token, onSaved }: PageProps) {
  const [items, setItems] = useState<Slogan[]>([]);
  const [item, setItem] = useState<Slogan | null>(null);
  const [text, setText] = useState('');
  const [active, setActive] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  useEffect(() => {
    let current = true;
    setError(''); setDone(false); setItem(null);
    if (mode === 'create') { setText(''); setActive(true); return; }
    if (mode !== 'index' && !id) { setError('Slogan ID is required.'); return; }
    // There is no GET /api/slogans/{id}; select the item from the admin list.
    adminRequest<Slogan[]>('/api/slogans', token).then(data => {
      if (!current) return;
      setItems(data);
      if (mode !== 'index') {
        const selected = data.find(s => String(s.id) === id);
        if (!selected) { setError('Slogan not found.'); return; }
        setItem(selected); setText(selected.text); setActive(selected.isActive);
      }
    }).catch(e => { if (current) setError(e.message); });
    return () => { current = false; };
  }, [mode, id, token]);
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await adminRequest<Slogan>(mode === 'create' ? '/api/slogans' : `/api/slogans/${encodeURIComponent(id!)}`, token, mode === 'create' ? 'POST' : 'PUT', { text: text.trim(), isActive: active });
      setDone(true); onSaved?.();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  async function remove(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { await adminRequest(`/api/slogans/${encodeURIComponent(id!)}`, token, 'DELETE'); setDone(true); onSaved?.(); }
    catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  async function toggle(s: Slogan) {
    setError('');
    try {
      // PUT accepts optional fields. Sending only isActive avoids overwriting
      // a concurrently edited slogan with stale table text.
      const updated = await adminRequest<Slogan>(`/api/slogans/${s.id}`, token, 'PUT', { isActive: !s.isActive });
      setItems(previous => previous.map(row => row.id === s.id ? updated : row));
    } catch (e) { setError((e as Error).message); }
  }
  const cancel = <Link to="/admin/slogans" className="btn btn-secondary">Cancel</Link>;
  return <section>
    {error && <div className="alert alert-danger text-danger" role="alert">{error}</div>}
    {done && <div className="alert alert-success" role="status">Saved. <Link to="/admin/slogans">Back to slogans</Link></div>}
    {mode === 'index' && <><div className="d-flex justify-content-between align-items-center mb-4"><h2>Manage Slogans</h2><Link to="/admin/slogans/create" className="btn btn-primary">Create New Slogan</Link></div>{items.length ? <div className="table-responsive"><table className="table table-hover"><thead><tr><th>ID</th><th>Content</th><th>Active</th><th>Created</th><th>Actions</th></tr></thead><tbody>{items.map(s => <tr key={s.id}><td>{s.id}</td><td>{s.text}</td><td><div className="form-check"><input type="checkbox" className="form-check-input" id={`active-${s.id}`} checked={s.isActive} onChange={() => void toggle(s)} /><label className="form-check-label" htmlFor={`active-${s.id}`}>{s.isActive ? 'Active' : 'Inactive'}</label></div></td><td>{shortDate(s.createdAt)}</td><td><Link className="btn btn-sm btn-warning" to={`/admin/slogans/edit/${s.id}`}>Edit</Link> <Link className="btn btn-sm btn-danger" to={`/admin/slogans/delete/${s.id}`}>Delete</Link></td></tr>)}</tbody></table></div> : !error && <div className="alert alert-info"><p className="mb-0">No slogans found. <Link to="/admin/slogans/create">Create your first slogan</Link></p></div>}</>}
    {(mode === 'create' || mode === 'edit') && <><h2>{mode === 'create' ? 'Create New Slogan' : 'Edit Slogan'}</h2><form onSubmit={save}><div className="mb-3"><label className="form-label" htmlFor="slogan-text">Content</label><textarea id="slogan-text" className="form-control" rows={3} maxLength={500} required value={text} onChange={e => setText(e.target.value)} placeholder="Enter slogan text (e.g., 'Your AI workspace, everywhere')" /><small className="form-text text-muted">The slogan text displayed to users (max 500 characters).</small></div><div className="mb-3 form-check"><input id="slogan-active" type="checkbox" className="form-check-input" checked={active} onChange={e => setActive(e.target.checked)} /><label htmlFor="slogan-active" className="form-check-label">Active</label><small className="form-text text-muted d-block">Only active slogans appear in the random rotation.</small></div><div className="d-flex justify-content-between">{cancel}<button className="btn btn-primary" disabled={busy || (mode === 'edit' && !item)}>{mode === 'create' ? 'Save' : 'Save Changes'}</button></div></form></>}
    {mode === 'delete' && <><h2>Delete Slogan</h2><div className="alert alert-warning"><h4>Are you sure you want to delete this slogan?</h4><p>This action cannot be undone.</p></div>{item && <div className="card mb-4"><div className="card-body"><dl className="row"><dt className="col-sm-3">ID:</dt><dd className="col-sm-9">{item.id}</dd><dt className="col-sm-3">Content:</dt><dd className="col-sm-9">{item.text}</dd><dt className="col-sm-3">Active:</dt><dd className="col-sm-9"><span className={`badge ${item.isActive ? 'bg-success' : 'bg-secondary'}`}>{item.isActive ? 'Active' : 'Inactive'}</span></dd><dt className="col-sm-3">Created:</dt><dd className="col-sm-9">{shortDate(item.createdAt)}</dd></dl></div></div>}<form onSubmit={remove}><div className="d-flex justify-content-between">{cancel}<button className="btn btn-danger" disabled={busy || !item}>Delete Slogan</button></div></form></>}
  </section>;
}
