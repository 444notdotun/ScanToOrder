// Where the API lives. BACKEND_URL (set in Netlify) is the setting; until it is set, the old address keeps working.
// This is the only place that address is written.
export const BACKEND_URL = (process.env.BACKEND_URL || 'https://scantoorder.onrender.com').replace(/\/+$/, '');
