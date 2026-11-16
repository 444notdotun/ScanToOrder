import { initialsOf, roleLabel } from '@/components/staff/StaffHeader';
import type { WorkerProfile } from '@/store/authStore';

// The name card on paper, for a sheet: the initials circle, the name and "{Role} · {Business}".
export default function StaffHeaderCard({ profile }: { profile: WorkerProfile }) {
  const name = profile.fullName?.trim() || profile.username;
  return (
    <div className="flex items-center gap-3 rounded-2xl bg-paperlight p-3">
      <span aria-hidden="true" className="flex h-12 w-12 flex-none items-center justify-center rounded-full bg-mustard font-heading text-xl text-bottle">
        {initialsOf(name)}
      </span>
      <div className="min-w-0">
        <div className="break-words text-base font-bold text-ink">{name}</div>
        <div className="break-words text-sm text-quiet">
          {roleLabel(profile)} · {profile.businessName ?? 'Plately'}
        </div>
      </div>
    </div>
  );
}
