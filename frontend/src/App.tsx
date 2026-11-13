/**
 * Root component. M0 renders the application shell placeholder; routing, auth and data
 * providers are added in M4 (docs/plan/12-implementation-roadmap.md).
 */
export function App() {
  return (
    <main id="main" className="mx-auto max-w-5xl px-4 py-8 font-sans">
      <h1 className="text-3xl font-semibold text-brand-800">Plant Shop</h1>
      <p className="mt-2 text-neutral-700">
        Indoor plants for every room. The store is coming soon.
      </p>
    </main>
  );
}
