import { api } from '@/lib/api';

export interface LoginResponse {
  token: string;
  username: string;
  role: string;
}

export const authService = {
  login: async (username: string, password: string): Promise<LoginResponse> => {
    return api.post('/api/v1/auth/login', 
      {
        username: username.trim(),
        password: password
      }, 
      {
        headers: {
          'Content-Type': 'application/json'
        }
      }
    ).then((res: any) => {
      // Return the extracted payload data
      return res as LoginResponse;
    });
  }
};
