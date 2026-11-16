'use client';

import { useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, MoreMenu, Sheet, Switch } from '@/components/plately/ui';
import PageHead, { Empty } from '@/components/staff/PageHead';
import PlateForm from '@/components/staff/PlateForm';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { managerApi, useCategories, usePlates, type Plate } from '@/lib/manager';
import { naira } from '@/lib/money';
import type { StockItem } from '@/lib/staff';

export default function MenuPage() {
  const { lost } = useConnection();
  const categories = useCategories();
  const plates = usePlates();
  const act = useAction();
  const [editing, setEditing] = useState<StockItem | null>(null);
  const [removing, setRemoving] = useState<StockItem | null>(null);
  const [removingSection, setRemovingSection] = useState<{ id: string; name: string } | null>(null);

  const sections = categories.data ?? [];
  const all = plates.data ?? [];
  const sheetOpen = editing !== null || removing !== null || removingSection !== null;

  // A new section is made first, then the plate goes into it.
  const save = (id: string | null) => async (plate: Plate, newSection: string | null) =>
    act.run(id ?? 'new', async () => {
      if (newSection) await managerApi.addCategory(newSection);
      if (id) await managerApi.editPlate(id, plate);
      else await managerApi.addPlate(plate);
    });

  const lonely = sections.filter((section) => !all.some((plate) => plate.categoryName === section.categoryName));
  const grouped = sections
    .map((section) => ({ section, items: all.filter((plate) => plate.categoryName === section.categoryName) }))
    .filter((group) => group.items.length > 0);

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Menu" line="What guests can order. A change shows on their phones straight away." />

      <div id={FAILURE_ID}>{act.failure && !sheetOpen ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_2fr]">
        <Card className="h-fit">
          <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Add to the menu</h2>
          {categories.isPending ? (
            <p className="text-quiet">One moment…</p>
          ) : (
            <PlateForm categories={sections} submitLabel="Add to the menu" busy={lost || act.busy !== null} onSubmit={save(null)} />
          )}
        </Card>

        <div className="flex flex-col gap-5">
          {plates.isError ? <FailureCard title="We couldn't load the menu" message={failureOf(plates.error).message} /> : null}
          {plates.data && all.length === 0 ? <Empty>Your menu is empty. Start with the plate everyone orders.</Empty> : null}

          {grouped.map(({ section, items }) => (
            <section key={section.categoryId} aria-label={section.categoryName} className="flex flex-col gap-2.5">
              <h2 className="font-heading text-[24px] font-normal">{section.categoryName}</h2>
              <ul className="flex flex-col gap-2.5">
                {items.map((plate) => (
                  <li key={plate.itemId} className="flex flex-wrap items-center gap-3 rounded-lg bg-paperlight p-3.5 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)]">
                    <div className="min-w-[180px] flex-1">
                      <div id={`plate-${plate.itemId}`} className="font-bold leading-snug">{plate.itemName}</div>
                      {plate.itemDescription ? <div className="text-sm leading-snug text-quiet">{plate.itemDescription}</div> : null}
                      <div className="font-heading text-lg text-bottle">{naira(plate.itemPrice)}</div>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <div className="flex items-center gap-2.5">
                        <Switch
                          checked={plate.available}
                          labelledBy={`plate-${plate.itemId}`}
                          disabled={lost || act.busy !== null}
                          onChange={() => void act.run(plate.itemId, () => managerApi.togglePlate(plate.itemId))}
                        />
                        <span className="w-[4.5rem] text-sm font-bold text-quiet">{plate.available ? 'On the menu' : 'Switched off'}</span>
                      </div>
                      <Button full={false} tone="outline" disabled={lost || act.busy !== null} onClick={() => setEditing(plate)}>
                        Edit
                      </Button>
                      <MoreMenu label={`More for ${plate.itemName}`} items={[{ label: `Delete ${plate.itemName}`, danger: true, onSelect: () => setRemoving(plate) }]} />
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          ))}

          {lonely.length > 0 ? (
            <section aria-label="Empty sections" className="flex flex-col gap-2.5">
              <h2 className="font-heading text-[24px] font-normal">Sections with nothing in them</h2>
              <ul className="flex flex-wrap gap-2.5">
                {lonely.map((section) => (
                  <li key={section.categoryId} className="flex items-center gap-2 rounded-full border-[1.5px] border-cream/45 py-1 pl-4 pr-1 text-cream">
                    <span className="font-bold">{section.categoryName}</span>
                    <Button
                      full={false}
                      tone="ghost"
                      aria-label={`Remove the section ${section.categoryName}`}
                      disabled={lost || act.busy !== null}
                      onClick={() => setRemovingSection({ id: section.categoryId, name: section.categoryName })}
                    >
                      Remove
                    </Button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      </div>

      <Sheet open={editing !== null} onClose={() => { setEditing(null); act.clear(); }} label="Edit this plate">
        <h2 className="mb-3 font-heading text-[28px] font-normal text-bottle">Edit {editing?.itemName}</h2>
        {editing ? (
          <PlateForm
            key={editing.itemId}
            categories={sections}
            initial={{ itemName: editing.itemName, itemDescription: editing.itemDescription ?? '', categoryName: editing.categoryName ?? '', itemPrice: editing.itemPrice ?? 0, imageUrl: editing.imageUrl ?? '' }}
            submitLabel="Save"
            busy={lost || act.busy !== null}
            onSubmit={async (plate, newSection) => {
              const worked = await save(editing.itemId)(plate, newSection);
              if (worked) setEditing(null);
              return worked;
            }}
            onCancel={() => { setEditing(null); act.clear(); }}
          />
        ) : null}
        {act.failure ? <FailureCard className="mt-3" title={act.failure.title} message={act.failure.message} /> : null}
      </Sheet>

      <Sheet open={removing !== null} onClose={() => { setRemoving(null); act.clear(); }} label="Delete this plate">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Delete {removing?.itemName}?</h2>
        <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">This can&apos;t be undone. To hide it for now, switch it off instead.</p>
        {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (removing && (await act.run(removing.itemId, () => managerApi.removePlate(removing.itemId)))) setRemoving(null);
            }}
          >
            Yes, delete it
          </Button>
          <Button big tone="outline" onClick={() => { setRemoving(null); act.clear(); }}>
            Keep it
          </Button>
        </div>
      </Sheet>

      <Sheet open={removingSection !== null} onClose={() => { setRemovingSection(null); act.clear(); }} label="Remove this section">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Remove {removingSection?.name}?</h2>
        {act.failure ? <FailureCard className="my-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="mt-4 flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (removingSection && (await act.run(removingSection.id, () => managerApi.removeCategory(removingSection.id)))) setRemovingSection(null);
            }}
          >
            Yes, remove it
          </Button>
          <Button big tone="outline" onClick={() => { setRemovingSection(null); act.clear(); }}>
            Keep it
          </Button>
        </div>
      </Sheet>
    </main>
  );
}
