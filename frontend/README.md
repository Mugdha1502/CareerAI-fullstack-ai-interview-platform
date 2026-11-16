# CareerAI — Frontend

React 19 + Vite single-page app for CareerAI. See the [main README](../README.md) for the full project overview.

```bash
npm install
cp .env.example .env      # Windows: copy .env.example .env
npm run dev               # http://localhost:5173
npm run build             # production build in dist/
npm run lint
```

Environment variables are documented in [`.env.example`](.env.example). Every `VITE_*` value is bundled into public JavaScript — never put secrets there.
