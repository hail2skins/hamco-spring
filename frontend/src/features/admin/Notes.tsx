import { useEffect, useState, type ClipboardEvent, type FormEvent } from 'react';
import { Link } from 'react-router';
import { adminRequest, shortDate, type Note, type PageProps } from './api';

export function AdminNotesPage({ mode, id, token, onSaved }: PageProps) {
  const [notes, setNotes] = useState<Note[]>([]);
  const [note, setNote] = useState<Note | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let current = true;
    setError(''); setDone(false); setNote(null);
    if (mode === 'create') { setTitle(''); setContent(''); return; }
    if (mode !== 'index' && !id) { setError('Note ID is required.'); return; }
    // There is no admin draft listing endpoint in this contract: the table
    // reflects published notes, and a delete removes a note rather than hiding it.
    adminRequest<Note[] | Note>(mode === 'index' ? '/api/notes' : `/api/notes/${encodeURIComponent(id!)}`, token)
      .then(data => {
        if (!current) return;
        if (Array.isArray(data)) setNotes(data);
        else { setNote(data); setTitle(data.title); setContent(data.content); }
      }).catch(e => { if (current) setError(e.message); });
    return () => { current = false; };
  }, [mode, id, token]);

  async function save(event: FormEvent) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      await adminRequest<Note>(mode === 'create' ? '/api/notes' : `/api/notes/${encodeURIComponent(id!)}`, token, mode === 'create' ? 'POST' : 'PUT', { title: title.trim(), content: content.trim() });
      setDone(true); onSaved?.();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  async function remove(event: FormEvent) {
    event.preventDefault(); setError(''); setBusy(true);
    try { await adminRequest(`/api/notes/${encodeURIComponent(id!)}`, token, 'DELETE'); setDone(true); onSaved?.(); }
    catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  function pasteImage(event: ClipboardEvent<HTMLTextAreaElement>) {
    const image = Array.from(event.clipboardData.items).find(item => item.type.startsWith('image/'))?.getAsFile();
    if (!image) return;
    event.preventDefault();
    const position = event.currentTarget.selectionStart;
    const original = event.currentTarget.value;
    // The Razor editor embeds clipboard images as Markdown data URLs. Read
    // asynchronously, retaining the original caret location and text.
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setContent(`${original.slice(0, position)}\n![pasted image](${reader.result})\n${original.slice(position)}`);
      }
    };
    reader.readAsDataURL(image);
  }
  const cancel = <Link to="/admin/notes" className="btn btn-secondary">Cancel</Link>;
  return <section>
    {error && <div role="alert" className="alert alert-danger text-danger">{error}</div>}
    {done && <div role="status" className="alert alert-success">Saved. <Link to="/admin/notes">Back to notes</Link></div>}
    {mode === 'index' && <>
      <div className="d-flex justify-content-between align-items-center mb-4"><h2>Manage Notes</h2><Link to="/admin/notes/create" className="btn btn-primary">Create New Note</Link></div>
      {notes.length ? <div className="table-responsive"><table className="table table-hover"><thead><tr><th>ID</th><th>Title</th><th>Slug</th><th>Created</th><th>Updated</th><th>Actions</th></tr></thead><tbody>{notes.map(n => <tr key={n.id}><td>{n.id}</td><td>{n.title}</td><td>{n.slug}</td><td>{shortDate(n.createdAt)}</td><td>{shortDate(n.updatedAt)}</td><td><Link className="btn btn-sm btn-warning" to={`/admin/notes/edit/${n.id}`}>Edit</Link> <Link className="btn btn-sm btn-danger" to={`/admin/notes/delete/${n.id}`}>Delete</Link></td></tr>)}</tbody></table></div> : !error && <div className="alert alert-info"><p className="mb-0">No notes found. <Link to="/admin/notes/create">Create your first note</Link></p></div>}
    </>}
    {(mode === 'create' || mode === 'edit') && <><h2>{mode === 'create' ? 'Create New Note' : 'Edit Note'}</h2><form onSubmit={save}>
      <div className="mb-3"><label htmlFor="note-title" className="form-label">Title</label><input id="note-title" className="form-control" maxLength={255} required value={title} onChange={e => setTitle(e.target.value)} /></div>
      <div className="mb-3"><label className="form-label">Slug</label><input className="form-control" value={note?.slug ?? ''} readOnly /><small className="form-text text-muted">Slug is generated from the title by the API and cannot be edited here.</small></div>
      <div className="mb-3"><label htmlFor="note-content" className="form-label">Content (Markdown)</label><textarea id="note-content" className="form-control" rows={20} required value={content} onChange={e => setContent(e.target.value)} onPaste={pasteImage} /><small className="form-text text-muted">Supports Markdown formatting.</small></div>
      <div className="d-flex justify-content-between">{cancel}<button className="btn btn-primary" disabled={busy || (mode === 'edit' && !note)}>{mode === 'create' ? 'Save' : 'Save Changes'}</button></div>
    </form></>}
    {mode === 'delete' && <><h2>Delete Note</h2><div className="alert alert-warning"><h4>Are you sure you want to delete this note?</h4><p>This action cannot be undone.</p></div>{note && <div className="card mb-4"><div className="card-body"><dl className="row"><dt className="col-sm-3">ID:</dt><dd className="col-sm-9">{note.id}</dd><dt className="col-sm-3">Title:</dt><dd className="col-sm-9">{note.title}</dd><dt className="col-sm-3">Slug:</dt><dd className="col-sm-9">{note.slug}</dd><dt className="col-sm-3">Created:</dt><dd className="col-sm-9">{shortDate(note.createdAt)}</dd><dt className="col-sm-3">Updated:</dt><dd className="col-sm-9">{shortDate(note.updatedAt)}</dd></dl><div className="mt-3"><h5>Content Preview:</h5><div className="border p-3 bg-light">{note.content.slice(0, 300)}{note.content.length > 300 ? '...' : ''}</div></div></div></div>}<form onSubmit={remove}><div className="d-flex justify-content-between">{cancel}<button className="btn btn-danger" disabled={busy || !note}>Delete Note</button></div></form></>}
  </section>;
}
