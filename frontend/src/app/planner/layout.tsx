'use client';

import type { ReactNode } from 'react';
import { PlannerBar, PlannerNav } from '@/components/staff/RoleBars';
import StaffShell from '@/components/staff/StaffShell';

export default function PlannerLayout({ children }: { children: ReactNode }) {
  return (
    <StaffShell allow={['MANAGER']} workspace="EVENT_PLANNER" nav={<PlannerNav />}>
      {children}
      <PlannerBar />
    </StaffShell>
  );
}
