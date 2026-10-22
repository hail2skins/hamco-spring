import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// Test isolation matters: leftover DOM or mocks can make the next page pass
// for the wrong reason, especially when routes share the same masthead.
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
