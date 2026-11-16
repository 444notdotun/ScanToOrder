'use client';

import { useState, type FormEvent } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field, Sheet } from '@/components/plately/ui';
import PageHead, { Empty } from '@/components/staff/PageHead';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { managerApi, useTeam, type Staff } from '@/lib/manager';

const ROLES = [
  { id: 'CHEF', label: 'Chef' },
  { id: 'WAITER', label: 'Waiter' },
];

const roleName = (role: string) => (role === 'CHEF' ? 'Chef' : role === 'WAITER' ? 'Waiter' : 'Manager');

export default function TeamPage() {
  const { lost } = useConnection();
  const team = useTeam();
  const act = useAction();
  const [name, setName] = useState('');
  const [handle, setHandle] = useState('');
  const [role, setRole] = useState('WAITER');
  const [errors, setErrors] = useState<Record<string, string>>({});
  // The temporary password is shown once, here, and never again.
  const [made, setMade] = useState<Staff | null>(null);
  const [removing, setRemoving] = useState<Staff | null>(null);

  const add = async (event: FormEvent) => {
    event.preventDefault();
    const found: Record<string, string> = {};
    if (!name.trim()) found.name = "Tell us the person's name.";
    if (!handle.trim()) found.handle = 'Choose a short handle for the sign-in, such as ada.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    let created: Staff | null = null;
    const worked = await act.run('add', async () => {
      created = await managerApi.addStaff({ name: name.trim(), role, handle: handle.trim() });
    });
    if (worked && created) {
      setMade(created);
      setName('');
      setHandle('');
    }
  };

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Team" line="Everyone gets their own way in." />

      <div id={FAILURE_ID}>{act.failure && removing === null ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>

      {made ? (
        <Card className="animate-pop border-2 border-dashed border-hand/50">
          <h2 className="font-heading text-[24px] font-normal text-bottle">{made.fullName} can sign in</h2>
          <p className="mt-1 text-[15px] leading-snug text-quiet">Give them these. The password is shown only now, and they choose their own the first time they sign in.</p>
          <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-[17px]">
            <dt className="text-quiet">Username</dt>
            <dd className="font-bold text-ink">{made.username}</dd>
            <dt className="text-quiet">Temporary password</dt>
            <dd className="font-mono font-bold text-ink">{made.temporaryPassword}</dd>
          </dl>
          <Button className="mt-4 sm:w-auto" full={false} tone="outline" onClick={() => setMade(null)}>
            I have noted them down
          </Button>
        </Card>
      ) : null}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_2fr]">
        <Card className="h-fit">
          <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Bring someone in</h2>
          <form noValidate onSubmit={(event) => void add(event)} className="flex flex-col gap-3.5">
            <Field label="Their name" value={name} onChange={(event) => setName(event.target.value)} error={errors.name} autoComplete="off" />
            <Field
              label="Handle"
              value={handle}
              onChange={(event) => setHandle(event.target.value)}
              error={errors.handle}
              autoCapitalize="none"
              autoComplete="off"
              hint="They sign in as yourplace.handle."
            />
            <div>
              <div id="role-label" className="mb-1.5 text-sm font-bold text-bottle">
                They work as
              </div>
              <div role="group" aria-labelledby="role-label" className="flex gap-2">
                {ROLES.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    aria-pressed={role === item.id}
                    onClick={() => setRole(item.id)}
                    className={`min-h-[48px] flex-1 rounded-full border-[1.5px] px-4 font-bold ${role === item.id ? 'border-bottle bg-bottle text-cream' : 'border-rule2 text-bottle'}`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
            <Button type="submit" tone="primary" disabled={lost || act.busy !== null}>
              Add them
            </Button>
          </form>
        </Card>

        <section aria-label="Your team" className="flex flex-col gap-2.5">
          {team.isError ? <FailureCard title="We couldn't load the team" message={failureOf(team.error).message} /> : null}
          {team.data && team.data.length <= 1 ? <Empty>Nobody here yet. Add at least one chef and one waiter so orders have somewhere to go.</Empty> : null}
          <ul className="flex flex-col gap-2.5">
            {(team.data ?? []).map((person) => (
              <li key={person.workerId} className="flex items-center gap-3 rounded-lg bg-paperlight p-3.5 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)]">
                <div className="min-w-0 flex-1">
                  <div className="font-bold">{person.fullName}</div>
                  <div className="break-all text-sm text-quiet">
                    {roleName(person.role)} · {person.username}
                  </div>
                </div>
                {person.role !== 'MANAGER' ? (
                  <Button full={false} tone="outline" aria-label={`Remove ${person.fullName}`} disabled={lost || act.busy !== null} onClick={() => setRemoving(person)}>
                    Remove
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      </div>

      <Sheet open={removing !== null} onClose={() => { setRemoving(null); act.clear(); }} label="Remove this person">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Remove {removing?.fullName}?</h2>
        <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">They are signed out at once and cannot sign in again.</p>
        {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (removing && (await act.run(removing.workerId, () => managerApi.removeStaff(removing.workerId)))) setRemoving(null);
            }}
          >
            Yes, remove them
          </Button>
          <Button big tone="outline" onClick={() => { setRemoving(null); act.clear(); }}>
            Keep them
          </Button>
        </div>
      </Sheet>
    </main>
  );
}
