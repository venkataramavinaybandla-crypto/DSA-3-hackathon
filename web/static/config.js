/* ============================================================================
   CERBERUS SYSTEM — runtime configuration
   ----------------------------------------------------------------------------
   This file is the single place to point the dashboard at a backend.

   Leave it empty (the default) for both of these cases:
     * local development  — the FastAPI server serves this page, so the API is
                            on the same origin as the dashboard;
     * single-service deploy — the backend serves both API and dashboard.

   Set it to the deployed API origin when the dashboard is hosted separately
   (for example a static host serving web/static while the API runs elsewhere):

     window.CERBERUS_API_URL = 'https://cerberus-api.example.edu';

   Resolution order used by app.js (first match wins):
     1. ?api=<url> query parameter        (handy for quick testing)
     2. localStorage['cerberusApiBase']   (per-browser override)
     3. window.CERBERUS_API_URL           (this file)
     4. the page's own origin             (same-origin: default)
     5. http://127.0.0.1:8005             (file:// fallback for local dev)
   ========================================================================== */

window.CERBERUS_API_URL = '';
