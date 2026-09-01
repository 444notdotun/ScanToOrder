import axios from 'axios';
import { toast } from 'sonner';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL?.replace(/\/+$/, '') || '';

// Authenticated client (sends HttpOnly cookies)
export const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Public client (never sends cookies, perfect for landing page)
export const publicApi = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Safe response interceptor: NEVER trigger window.location redirects
api.interceptors.response.use(
  (response) => {
    const resData = response.data;
    const successMessage = resData?.message || resData?.data?.message || (typeof resData?.data === 'string' ? resData.data : null);
    
    // Only toast on successful mutations
    if (
      resData &&
      resData.status === 'success' &&
      successMessage &&
      ['post', 'put', 'patch', 'delete'].includes(response.config.method?.toLowerCase() || '')
    ) {
      toast.success(successMessage);
    }
    return response;
  },
  (error) => {
    // Log errors cleanly for debugging without hijacking the DOM/routing
    if (error.response?.status === 401) {
      console.warn('API 401 Unauthorized:', error.config?.url);
    }

    // Extract error message for toast
    const responseData = error.response?.data;
    let parsedErrorMessage = 'An unexpected error occurred';
    
    if (responseData) {
      if (responseData.message) {
        parsedErrorMessage = responseData.message;
      } else if (responseData.error) {
        parsedErrorMessage = responseData.error;
      }
    } else if (error.message && !error.message.includes('status code')) {
      parsedErrorMessage = error.message;
    }

    // Clean up backend formatting artifacts
    parsedErrorMessage = parsedErrorMessage
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#x27;/g, "'")
      .replace(/&#x2F;/g, '/');

    // Only toast actual errors to avoid spamming for things like background status checks
    if (error.response?.status !== 401 && error.response?.status !== 403) {
      toast.error(parsedErrorMessage);
    } else {
      // Per rules: ignore silent background polling 401s globally
      // Let the individual pages handle redirection or specialized auth error UI if necessary
      console.warn('Silent 401/403 ignored by global toast interceptor');
    }

    return Promise.reject(error);
  }
);

export default api;
