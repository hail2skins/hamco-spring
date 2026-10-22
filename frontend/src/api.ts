export interface Note {
  id: number;
  title: string;
  slug: string;
  content: string;
  userId: string | null;
  createdAt: string;
  updatedAt: string;
}

/** API errors keep the HTTP status so 401/403/404 are not presented as empty lists. */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message); }
}

export async function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, { credentials: 'same-origin', signal });
  if (!response.ok) throw new ApiError(response.status, `Request failed (${response.status})`);
  return response.json() as Promise<T>;
}

export function plainExcerpt(markdown: string, limit: number): string {
  // The server returns Markdown; excerpts should look like the Razor page's
  // plaintext cards, not expose formatting punctuation or raw HTML tags.
  const text = markdown.replace(/<[^>]*>/g, ' ').replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/[`*_#>~]/g, '')
    .replace(/\s+/g, ' ').trim();
  return text.length > limit ? `${text.slice(0, limit).trimEnd()}...` : text;
}
