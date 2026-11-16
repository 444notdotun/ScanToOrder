'use client';

import { useState, type FormEvent } from 'react';
import { Button, Field, SelectField } from '@/components/plately/ui';
import type { Category, Plate } from '@/lib/manager';

const NEW_SECTION = '__new__';

// One plate: what it is called, how it is described, its section and its price in naira.
export default function PlateForm({
  categories,
  initial,
  submitLabel,
  busy,
  onSubmit,
  onCancel,
}: {
  categories: Category[];
  initial?: Partial<Plate>;
  submitLabel: string;
  busy: boolean;
  onSubmit: (plate: Plate, newSection: string | null) => Promise<boolean>;
  onCancel?: () => void;
}) {
  const [name, setName] = useState(initial?.itemName ?? '');
  const [description, setDescription] = useState(initial?.itemDescription ?? '');
  const [section, setSection] = useState(initial?.categoryName ?? '');
  // Until one is chosen, the first section stands; with no sections yet, a new one is made.
  const shown = section || categories[0]?.categoryName || NEW_SECTION;
  const [newSection, setNewSection] = useState('');
  const [price, setPrice] = useState(initial?.itemPrice != null ? String(initial.itemPrice) : '');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found: Record<string, string> = {};
    if (!name.trim()) found.name = 'Give the plate a name.';
    const naira = Number(price);
    if (!price.trim() || !Number.isInteger(naira) || naira < 1) found.price = 'Price it in whole naira, for example 3500.';
    const creating = shown === NEW_SECTION;
    if (creating && !newSection.trim()) found.section = 'Name the new section.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    const worked = await onSubmit(
      {
        itemName: name.trim(),
        itemDescription: description.trim(),
        categoryName: creating ? newSection.trim() : shown,
        itemPrice: naira,
      },
      creating ? newSection.trim() : null,
    );
    // A new plate clears the form for the next one; an edit leaves it as it is.
    if (worked && !initial) {
      setName('');
      setDescription('');
      setPrice('');
      setNewSection('');
    }
  };

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-3.5">
      <Field label="Name" value={name} onChange={(event) => setName(event.target.value)} error={errors.name} placeholder="Jollof rice and grilled chicken" />
      <Field
        label="Description"
        hint="Say it the way you would say it at the table."
        value={description}
        onChange={(event) => setDescription(event.target.value)}
      />
      <SelectField label="Section" value={shown} onChange={(event) => setSection(event.target.value)}>
        {categories.map((category) => (
          <option key={category.categoryId} value={category.categoryName}>
            {category.categoryName}
          </option>
        ))}
        <option value={NEW_SECTION}>A new section…</option>
      </SelectField>
      {shown === NEW_SECTION ? (
        <Field label="Name of the new section" value={newSection} onChange={(event) => setNewSection(event.target.value)} error={errors.section} />
      ) : null}
      <Field label="Price in naira" inputMode="numeric" value={price} onChange={(event) => setPrice(event.target.value)} error={errors.price} placeholder="3500" />
      <div className="flex flex-col gap-2.5 sm:flex-row">
        <Button type="submit" tone="primary" disabled={busy}>
          {submitLabel}
        </Button>
        {onCancel ? (
          <Button tone="outline" onClick={onCancel}>
            Never mind
          </Button>
        ) : null}
      </div>
    </form>
  );
}
