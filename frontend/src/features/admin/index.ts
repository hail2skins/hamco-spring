// App owns routing and authentication; pages receive the in-memory admin JWT.
// Mount index/create/edit/delete routes for notes and slogans and index/create/
// show (optional delete compatibility route) for keys. Never pass secrets via URL.
export { AdminNotesPage } from './Notes';
export { AdminSlogansPage } from './Slogans';
export { AdminApiKeysPage } from './ApiKeys';
export { adminRequest } from './api';
export type { Note, Slogan, ApiKey, IssuedKey, PageProps } from './api';
export type { ApiKeysPageProps } from './ApiKeys';
