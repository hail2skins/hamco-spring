import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from './App';
import './styles/source.css';
import './styles/app.css';

// BrowserRouter preserves the ASP.NET page URLs (including direct/deep links)
// while React changes the view without replacing the whole document.
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter><App /></BrowserRouter>
  </React.StrictMode>,
);
