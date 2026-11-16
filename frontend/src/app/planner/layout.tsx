'use client';

import type { ReactNode } from 'react';
import StaffShell from '@/components/staff/StaffShell';

export default function PlannerLayout({ children }: { children: ReactNode }) {
  return (
    <StaffShell allow={['MANAGER']} workspace="EVENT_PLANNER">
      {children}
    </StaffShell>
  );
}
