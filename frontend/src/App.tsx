import { useEffect, useState } from 'react';
import { Link, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGithub, faLinkedinIn, faTwitter } from '@fortawesome/free-brands-svg-icons';
import { faCircle } from '@fortawesome/free-solid-svg-icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import rehypeHighlight from 'rehype-highlight';
import { apiGet, plainExcerpt, type Note } from './api';
import { AuthProvider, ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage,
  VerificationPendingPage, VerifyEmailPage, useAuth } from './features/auth';
import { AdminNotesPage, AdminSlogansPage, AdminApiKeysPage } from './features/admin';

const images = [1, 2, 3, 4].map(number => `/img/main/hammy${number}.png`);
const fallbackSlogan = 'Your trusted partner in Internet solutions';

function useNotes() {
  const [notes, setNotes] = useState<Note[] | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    const request = new AbortController();
    apiGet<Note[]>('/api/notes', request.signal)
      .then(rows => setNotes([...rows].sort((a, b) => b.createdAt.localeCompare(a.createdAt))))
      .catch(() => { if (!request.signal.aborted) setError(true); });
    return () => request.abort();
  }, []);
  return { notes, error };
}

function Footer() {
  return <footer className="border-top"><div className="container px-4 px-lg-5"><div className="row gx-4 gx-lg-5 justify-content-center"><div className="col-md-10 col-lg-10 col-xl-8">
    <ul className="list-inline text-center">
      <li className="list-inline-item"><a aria-label="Twitter" href="https://twitter.com/TechFocusedGeek"><span className="fa-stack fa-lg"><FontAwesomeIcon icon={faCircle} className="fa-stack-2x" /><FontAwesomeIcon icon={faTwitter} className="fa-twitter fa-stack-1x fa-inverse" /></span></a></li>
      <li className="list-inline-item"><a aria-label="LinkedIn" href="https://www.linkedin.com/in/art-mills-2415a33/"><span className="fa-stack fa-lg"><FontAwesomeIcon icon={faCircle} className="fa-stack-2x" /><FontAwesomeIcon icon={faLinkedinIn} className="fa-linkedin-in fa-stack-1x fa-inverse" /></span></a></li>
      <li className="list-inline-item"><a aria-label="GitHub" href="https://github.com/hail2skins"><span className="fa-stack fa-lg"><FontAwesomeIcon icon={faCircle} className="fa-stack-2x" /><FontAwesomeIcon icon={faGithub} className="fa-github fa-stack-1x fa-inverse" /></span></a></li>
    </ul>
    <div className="small text-center text-muted fst-italic">Copyright © Hamco Internet Solutions {new Date().getFullYear()}</div>
  </div></div></div></footer>;
}

function Home() {
  const { notes, error } = useNotes();
  return <>
    <div className="post-preview"><h2 className="post-title">Welcome to Hamco Internet Solutions</h2><p className="post-meta">Your trusted partner in web development and internet solutions.</p></div>
    <hr className="my-4" />
    <div className="post-preview"><h3>What We Do</h3><p>We specialize in building modern web applications using cutting-edge technology. From API development to full-stack solutions, we deliver quality software that meets your needs.</p></div>
    <hr className="my-4" />
    <div className="post-preview"><div className="d-flex justify-content-between align-items-center mb-3"><h3 className="mb-0">Recent Notes</h3><Link className="btn btn-sm btn-outline-primary" to="/notes">View All</Link></div>
      {error && <p role="alert" className="error-state">Could not load notes. Please try again.</p>}
      {!error && !notes && <p className="loading-state">Loading notes…</p>}
      {notes?.length === 0 && <p className="text-muted">No notes yet. Check back soon for technical articles and insights.</p>}
      {notes?.slice(0, 5).map(note => <div className="card mb-3" key={note.id}><div className="card-body"><Link className="text-decoration-none" to={`/notes/${note.id}`}><h5 className="card-title fw-bold">{note.title}</h5></Link>
        <p className="card-text">{plainExcerpt(note.content, 500)}</p><p className="card-text"><small className="text-muted">Posted {new Date(note.createdAt).toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' })}</small></p>
      </div></div>)}
    </div>
  </>;
}

function NotesList() {
  const { notes, error } = useNotes();
  return <><h1 className="mb-4">Deep Thoughts</h1>
    {error && <p role="alert" className="error-state">Could not load notes. Please try again.</p>}
    {!error && !notes && <p className="loading-state">Loading notes…</p>}
    {notes?.length === 0 && <div className="text-center py-5"><h3>No notes yet</h3><p className="text-muted">Check back soon for technical articles and insights.</p></div>}
    <div className="notes-list">{notes?.map(note => <article className="note-item mb-4 pb-4 border-bottom" key={note.id}><h5 className="mb-2"><Link className="text-decoration-none" to={`/notes/${note.id}`}>{note.title}</Link></h5>
      <p className="text-muted mb-1">{plainExcerpt(note.content, 100)}</p><small className="text-muted">{new Date(note.createdAt).toLocaleDateString('en-US', { month: '2-digit', day: '2-digit', year: 'numeric' })}</small>
    </article>)}</div>
  </>;
}

function NoteDetail({ onLoad }: { onLoad: (note: Note | null) => void }) {
  const { id } = useParams();
  const [note, setNote] = useState<Note | null>(null);
  const [error, setError] = useState<number | null>(null);
  useEffect(() => {
    const request = new AbortController();
    onLoad(null);
    apiGet<Note>(`/api/notes/${id}`, request.signal).then(result => { setNote(result); onLoad(result); })
      .catch((reason: unknown) => { if (!request.signal.aborted) setError((reason as { status?: number }).status ?? 500); });
    return () => request.abort();
  }, [id, onLoad]);
  if (error) return <p role="alert">{error === 404 ? 'Note not found.' : 'Could not load this note.'} <Link to="/notes">Back to Notes</Link></p>;
  if (!note) return <p className="loading-state">Loading note…</p>;
  return <article className="mb-4 note-content"><ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize, rehypeHighlight]}>{note.content}</ReactMarkdown><hr className="my-4" />
    <div className="d-flex justify-content-between align-items-center"><div className="text-muted small"><span>Created: {new Date(note.createdAt).toLocaleString('en-US', { dateStyle: 'long', timeStyle: 'short' })}</span></div><Link className="btn btn-outline-primary btn-sm" to="/notes">← Back to Notes</Link></div>
  </article>;
}

function About() {
  return <><h2>About Hamco Internet Solutions</h2><p>Founded with a passion for technology and innovation, Hamco Internet Solutions provides comprehensive web development services to businesses and individuals.</p><h3>Our Mission</h3><p>To deliver high-quality, scalable, and maintainable software solutions that empower our clients to succeed in the digital world.</p><h3>Technologies We Use</h3><ul><li>.NET Core / ASP.NET Core</li><li>Entity Framework Core</li><li>PostgreSQL</li><li>React &amp; Modern JavaScript</li><li>Docker &amp; Cloud Deployment</li></ul><h3>Get in Touch</h3><p>Interested in working together? Check out our <a href="https://github.com/hail2skins">GitHub</a> or connect on <a href="https://www.linkedin.com/in/art-mills-2415a33/">LinkedIn</a>.</p></>;
}

function AdminRoute({ kind, mode }: { kind: 'notes' | 'slogans' | 'apikeys'; mode: 'index' | 'create' | 'edit' | 'delete' | 'show' }) {
  const { user, token } = useAuth();
  const { id } = useParams();
  const navigate = useNavigate();
  if (!user) return <p role="alert">Admin access requires authentication. <Link to="/auth/login">Sign in</Link></p>;
  if (!user.roles?.includes('Admin')) return <p role="alert">Access denied. This page requires an administrator.</p>;
  // The profile cookie restores GET identity on refresh, not the bearer JWT
  // required for mutations. Do not render a seemingly usable write form.
  if (!token) return <p role="alert">Sign in again to manage content. <Link to="/auth/login">Sign in</Link></p>;
  if (kind === 'notes') return <AdminNotesPage mode={mode as 'index' | 'create' | 'edit' | 'delete'} id={id} token={token} onSaved={() => navigate('/admin/notes')} />;
  if (kind === 'slogans') return <AdminSlogansPage mode={mode as 'index' | 'create' | 'edit' | 'delete'} id={id} token={token} onSaved={() => navigate('/admin/slogans')} />;
  return <AdminApiKeysPage mode={mode as 'index' | 'create' | 'show' | 'delete'} id={id} token={token} />;
}

function Site() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, refreshProfile } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [image, setImage] = useState(images[0]);
  const [detailNote, setDetailNote] = useState<Note | null>(null);
  const [registrationEnabled, setRegistrationEnabled] = useState(false);
  const [activeSlogan, setActiveSlogan] = useState(fallbackSlogan);
  useEffect(() => { void refreshProfile().catch(() => { /* Offline pages remain readable. */ }); }, [refreshProfile]);
  useEffect(() => { setMenuOpen(false); setImage(images[Math.floor(Math.random() * images.length)]); }, [location.pathname]);
  useEffect(() => {
    const request = new AbortController();
    apiGet<{ registrationEnabled: boolean }>('/api/site/config', request.signal)
      .then(config => setRegistrationEnabled(config.registrationEnabled === true))
      .catch(() => { if (!request.signal.aborted) setRegistrationEnabled(false); });
    return () => request.abort();
  }, []);
  useEffect(() => {
    const request = new AbortController();
    // The Razor layout chooses a fresh active slogan on each page request.
    // Auth pages override it with their fixed instructional subtitle.
    apiGet<{ text: string }>('/api/site/slogan', request.signal)
      .then(result => setActiveSlogan(result.text || fallbackSlogan))
      .catch(() => { if (!request.signal.aborted) setActiveSlogan(fallbackSlogan); });
    return () => request.abort();
  }, [location.pathname]);
  const isHome = location.pathname === '/';
  const isAbout = location.pathname === '/about';
  const isNotes = location.pathname === '/notes';
  const isDetail = /^\/notes\/\d+$/.test(location.pathname);
  const routeName = location.pathname.split('/');
  const adminSection = routeName[1] === 'admin' ? routeName[2] : null;
  const adminAction = routeName[3];
  const authTitle: Record<string, [string, string]> = {
    login: ['Login', 'Welcome back'], register: ['Register', 'Create your account'],
    'forgot-password': ['Forgot Password', 'Reset your password'],
    'reset-password': ['Reset Password', 'Create a new password'],
    'verification-pending': ['Verify Your Email', 'Check your inbox'],
    'verify-email': ['Verify Email', 'Confirm your address'],
  };
  const adminTitle = adminSection === 'notes' ? 'Note' : adminSection === 'slogans' ? 'Slogan' : 'API Key';
  const heading = isHome ? 'Hamco Internet Solutions' : isAbout ? 'About Hamco' : isNotes ? 'Technical Notes & Articles'
    : isDetail ? (detailNote?.title ?? 'Note') : adminSection ? (adminAction ? `${adminAction === 'create' ? 'Create New' : adminAction === 'edit' ? 'Edit' : adminAction === 'delete' ? 'Delete' : 'Save Your'} ${adminTitle}` : adminSection === 'apikeys' ? 'API Key Management' : `Admin ${adminSection === 'notes' ? 'Notes' : 'Slogans'}`)
    : routeName[1] === 'auth' ? (authTitle[routeName[2]]?.[0] ?? 'Login') : 'Hamco Internet Solutions';
  const slogan = routeName[1] === 'auth' ? (authTitle[routeName[2]]?.[1] ?? '') : activeSlogan;
  useEffect(() => { document.title = `🐴 ${isHome ? 'Home' : isAbout ? 'About' : isNotes ? 'Notes' : heading} - Hamco Internet Solutions`; }, [isHome, isAbout, isNotes, heading]);
  return <>
    <nav className="navbar navbar-expand-lg navbar-light" id="mainNav" aria-label="Main navigation"><div className="container px-4 px-lg-5">
      <Link className="navbar-brand" to="/">Hamco Internet Solutions</Link>
      <button className="navbar-toggler" type="button" aria-expanded={menuOpen} aria-controls="navbarResponsive" aria-label="Toggle navigation" onClick={() => setMenuOpen(!menuOpen)}>Menu <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M2 5h20v2H2zm0 6h20v2H2zm0 6h20v2H2z" /></svg></button>
      <div className={`collapse navbar-collapse${menuOpen ? ' show' : ''}`} id="navbarResponsive"><ul className="navbar-nav ms-auto py-4 py-lg-0">
        <li className="nav-item"><Link className="nav-link px-lg-3 py-3 py-lg-4" to="/">Home</Link></li><li className="nav-item"><Link className="nav-link px-lg-3 py-3 py-lg-4" to={user?.roles?.includes('Admin') ? '/admin/notes' : '/notes'}>Notes</Link></li><li className="nav-item"><Link className="nav-link px-lg-3 py-3 py-lg-4" to="/about">About</Link></li>
        {user ? <>{user.roles?.includes('Admin') && <><li className="nav-item"><Link className="nav-link px-lg-3 py-3 py-lg-4" to="/admin/slogans">Slogans</Link></li><li className="nav-item"><Link className="nav-link px-lg-3 py-3 py-lg-4" to="/admin/apikeys">API Keys</Link></li></>}
          <li className="nav-item"><a className="nav-link px-lg-3 py-3 py-lg-4" href="/api/auth/logout" onClick={event => { event.preventDefault(); void logout().then(() => navigate('/')); }}>Logout</a></li></>
          : <><li className="nav-item"><Link className="nav-link px-lg-3 py-3 py-lg-4" to="/auth/register">Register</Link></li><li className="nav-item"><Link className="nav-link px-lg-3 py-3 py-lg-4" to="/auth/login">Login</Link></li></>}
      </ul></div>
    </div></nav>
    <header className="masthead" style={{ backgroundImage: `url('${image}')` }}><div className="container position-relative px-4 px-lg-5"><div className="row gx-4 gx-lg-5 justify-content-center"><div className="col-md-10 col-lg-10 col-xl-8"><div className="site-heading"><h1>{heading}</h1><span className="subheading">{slogan}</span>{isDetail && detailNote && <><br /><br /><span className="meta">Posted by Art Mills<p>{new Date(detailNote.createdAt).toLocaleDateString('en-US', { month: 'long', day: '2-digit', year: 'numeric' })}</p></span></>}</div></div></div></div></header>
    <div className="container px-4 px-lg-5"><div className="row gx-4 gx-lg-5 justify-content-center"><main className="col-md-10 col-lg-10 col-xl-8"><Routes>
      <Route path="/" element={<Home />} /><Route path="/about" element={<About />} /><Route path="/notes" element={<NotesList />} /><Route path="/notes/:id" element={<NoteDetail onLoad={setDetailNote} />} />
      <Route path="/auth/login" element={<LoginPage />} /><Route path="/auth/register" element={<RegisterPage registrationEnabled={registrationEnabled} />} /><Route path="/auth/verification-pending" element={<VerificationPendingPage />} /><Route path="/auth/verify-email" element={<VerifyEmailPage />} /><Route path="/auth/forgot-password" element={<ForgotPasswordPage />} /><Route path="/auth/reset-password" element={<ResetPasswordPage />} />
      <Route path="/admin/notes" element={<AdminRoute kind="notes" mode="index" />} /><Route path="/admin/notes/create" element={<AdminRoute kind="notes" mode="create" />} /><Route path="/admin/notes/edit/:id" element={<AdminRoute kind="notes" mode="edit" />} /><Route path="/admin/notes/delete/:id" element={<AdminRoute kind="notes" mode="delete" />} />
      <Route path="/admin/slogans" element={<AdminRoute kind="slogans" mode="index" />} /><Route path="/admin/slogans/create" element={<AdminRoute kind="slogans" mode="create" />} /><Route path="/admin/slogans/edit/:id" element={<AdminRoute kind="slogans" mode="edit" />} /><Route path="/admin/slogans/delete/:id" element={<AdminRoute kind="slogans" mode="delete" />} />
      <Route path="/admin/apikeys" element={<AdminRoute kind="apikeys" mode="index" />} /><Route path="/admin/apikeys/create" element={<AdminRoute kind="apikeys" mode="create" />} /><Route path="/admin/apikeys/show/:id" element={<AdminRoute kind="apikeys" mode="show" />} /><Route path="/admin/apikeys/delete/:id" element={<AdminRoute kind="apikeys" mode="delete" />} />
      <Route path="*" element={<p role="alert">Page not found. <Link to="/">Return to Home</Link></p>} />
    </Routes></main></div></div>
    <Footer />
  </>;
}

export default function App() { return <AuthProvider><Site /></AuthProvider>; }
