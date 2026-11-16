import { useQuery } from '@tanstack/react-query';
import { dataOf, http } from '@/lib/http';
import type { Period } from '@/lib/manager';
import { happened } from '@/lib/time';

export type TenantStatus = 'PENDING_APPROVAL' | 'DECLINED' | 'ACTIVE' | 'SUSPENDED';

export interface Tenant {
  tenantId: string;
  name: string;
  slug: string;
  type: 'RESTAURANT' | 'EVENT_PLANNER';
  status: TenantStatus;
  ownerName?: string;
  email?: string;
  phone?: string;
  declineReason?: string;
  createdAt?: string;
  approvedAt?: string;
}

export interface TenantAction {
  tenantId: string;
  status: TenantStatus;
  managerUsername?: string;
  emailSent: boolean;
  message: string;
}

export interface ProblemRefund {
  refundId: string;
  tenantName: string;
  orderNumber?: string;
  amount: number;
  reason?: string;
  status: string;
  dueSince?: string;
  overdue: boolean;
  reportedMissing: boolean;
}

export interface Earnings {
  period: Period;
  chargesKobo: number;
  eventFeesKobo: number;
  totalKobo: number;
  liveBusinesses: number;
  totalBusinesses: number;
  byRestaurant: Array<{ name: string; chargeKobo: number }>;
  eventFees: Array<{ eventName: string; plannerName: string; guests: number; amountKobo: number }>;
  failedPayments: number;
  refundsKobo: number;
  refundsCount: number;
}

export interface SignupForm {
  type: 'RESTAURANT' | 'EVENT_PLANNER';
  businessName: string;
  ownerName: string;
  email: string;
  phone: string;
  bankCode?: string;
  accountNumber?: string;
  accountName?: string;
}

export const platformApi = {
  signup: async (form: SignupForm) => dataOf<{ slug: string; status: string; message: string }>(await http.post('/api/v1/signup', form)),
  tenants: async (status?: string) => dataOf<Tenant[]>(await http.get('/api/v1/platform/tenants', { params: status ? { status } : undefined })),
  approve: async (id: string) => dataOf<TenantAction>(await http.post(`/api/v1/platform/tenants/${id}/approve`)),
  decline: async (id: string, reason: string) => dataOf<TenantAction>(await http.post(`/api/v1/platform/tenants/${id}/decline`, { reason: reason.trim() })),
  suspend: async (id: string) => dataOf<TenantAction>(await http.post(`/api/v1/platform/tenants/${id}/suspend`)),
  reinstate: async (id: string) => dataOf<TenantAction>(await http.post(`/api/v1/platform/tenants/${id}/reinstate`)),
  resend: async (id: string) => dataOf<TenantAction>(await http.post(`/api/v1/platform/tenants/${id}/resend-sign-in`)),
  refunds: async () => dataOf<ProblemRefund[]>(await http.get('/api/v1/platform/refunds', { params: { problem: true } })),
  earnings: async (period: Period) => dataOf<Earnings>(await http.get('/api/v1/platform/earnings', { params: { period } })),
};

// The platform has no live stream of its own, so these refetch whenever the owner comes back to the tab.
const again = { refetchOnWindowFocus: true } as const;
export const useTenants = (status?: string) => useQuery({ queryKey: ['tenants', status ?? 'all'], queryFn: () => platformApi.tenants(status), ...again });
export const useProblemRefunds = () => useQuery({ queryKey: ['problem-refunds'], queryFn: platformApi.refunds, ...again });
export const useEarnings = (period: Period) => useQuery({ queryKey: ['earnings', period], queryFn: () => platformApi.earnings(period), ...again });

export const SUGGESTED = [
  'The name on the bank account does not match the business',
  'We could not reach you on the phone number given',
  'The business details look incomplete',
];

// "Signed up 16 Nov, 9:07 am".
export function signedUp(iso?: string): string {
  return sent(iso).replace(/^Sent /, 'Signed up ');
}

// "Sent 16 Nov · 9:07 am".
export function sent(iso?: string): string {
  return iso ? `Sent ${happened(iso)}` : '';
}
