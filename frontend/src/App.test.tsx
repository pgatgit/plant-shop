import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { App } from './App';

describe('App', () => {
  it('renders the store heading as the single top-level heading', () => {
    render(<App />);

    expect(screen.getByRole('heading', { level: 1, name: 'Plant Shop' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('exposes a main landmark for the skip link target', () => {
    render(<App />);

    expect(screen.getByRole('main')).toHaveAttribute('id', 'main');
  });

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<App />);

    expect(await axe(container)).toHaveNoViolations();
  });
});
