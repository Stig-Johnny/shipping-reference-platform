// ParcelForm: weight, dimensions, origin, destination and service level, each with a visible label, unit and hint.

import { button, showErrorSummary } from '../../ui/components.ts';
import { el } from '../../ui/dom.ts';
import { selectField, textField } from '../../ui/fields.ts';
import type { Field } from '../../ui/fields.ts';
import { OTHER_COUNTRY, SERVED_COUNTRIES, orderedErrors } from './ParcelFormModel.ts';
import type { ParcelErrors, ParcelField, ParcelFields } from './ParcelFormModel.ts';

const COUNTRY_NAMES: Readonly<Record<string, string>> = {
  DE: 'Germany', DK: 'Denmark', FI: 'Finland', NL: 'Netherlands', NO: 'Norway', SE: 'Sweden',
};

export interface ParcelFormView {
  readonly element: HTMLFormElement;
  values(): ParcelFields;
  /** Shows each error next to its field and the summary above the form, and focuses the summary. */
  showErrors(errors: ParcelErrors, unplaced: readonly string[]): void;
  clearErrors(): void;
  setPending(pending: boolean): void;
}

const id = (field: ParcelField): string => `parcel-${field}`;

interface TextOptions {
  readonly numeric?: boolean;
  readonly upperCase?: boolean;
}

interface PlaceKeys {
  readonly country: ParcelField;
  readonly other: ParcelField;
  readonly postcode: ParcelField;
}

const ORIGIN: PlaceKeys = { country: 'originCountry', other: 'originOther', postcode: 'originPostcode' };
const DESTINATION: PlaceKeys = {
  country: 'destinationCountry', other: 'destinationOther', postcode: 'destinationPostcode',
};

function countryOptions(): Array<{ value: string; label: string }> {
  return [
    { value: '', label: 'Choose a country' },
    ...SERVED_COUNTRIES.map((code) => ({ value: code, label: `${code} ${new Map(Object.entries(COUNTRY_NAMES)).get(code) ?? ''}`.trim() })),
    { value: OTHER_COUNTRY, label: 'Other' },
  ];
}

export function parcelForm(initial: ParcelFields, onSubmit: (fields: ParcelFields) => void): ParcelFormView {
  const initialValues = new Map(Object.entries(initial));
  const fields = new Map<ParcelField, Field<HTMLInputElement | HTMLSelectElement>>();
  const text = (field: ParcelField, label: string, hint: string, options: TextOptions = {}): HTMLElement => {
    const created = textField({ id: id(field), label, hint, required: true, value: initialValues.get(field) ?? '', ...options });
    fields.set(field, created);
    return created.root;
  };
  const place = (keys: PlaceKeys, legend: string, example: string): HTMLFieldSetElement => {
    const country = selectField({
      id: id(keys.country), label: 'Country', hint: 'Served: DE, DK, FI, NL, NO, SE.', required: true,
      value: initialValues.get(keys.country) ?? '', options: countryOptions(),
    });
    fields.set(keys.country, country);
    const other = text(keys.other, 'Country code', 'Two capital letters, for example US.', { upperCase: true });
    const showOther = (): void => {
      other.hidden = country.control.value !== OTHER_COUNTRY;
    };
    country.control.addEventListener('change', showOther);
    showOther();
    return el('fieldset', { class: 'group' }, el('legend', { class: 'group__legend' }, legend),
      country.root, other, text(keys.postcode, 'Postcode', `For example ${example}.`, { upperCase: true }));
  };

  const summary = el('div', {});
  const submit = button('Get offers', 'primary', undefined, 'submit');
  const serviceLevel = selectField({
    id: id('serviceLevel'), label: 'Service level', hint: 'Any shows every level that is available.', required: false,
    value: initial.serviceLevel,
    options: [
      { value: '', label: 'Any' },
      { value: 'ECONOMY', label: 'Economy' },
      { value: 'STANDARD', label: 'Standard' },
      { value: 'EXPRESS', label: 'Express' },
    ],
  });
  fields.set('serviceLevel', serviceLevel);

  const element = el('form', { class: 'form parcel-form', novalidate: true, 'aria-label': 'Parcel' },
    summary,
    el('div', { class: 'form-grid' },
      text('weight', 'Weight (g)', 'Whole grams, 1 to 30000.', { numeric: true }),
      el('fieldset', { class: 'group' }, el('legend', { class: 'group__legend' }, 'Dimensions'),
        el('div', { class: 'inline-fields' },
          text('length', 'Length (mm)', '1 to 1200.', { numeric: true }),
          text('width', 'Width (mm)', '1 to 1200.', { numeric: true }),
          text('height', 'Height (mm)', '1 to 1200.', { numeric: true }))),
      place(ORIGIN, 'Origin', '0150'),
      place(DESTINATION, 'Destination', '11122'),
      serviceLevel.root),
    el('div', { class: 'button-row' }, submit));

  const values = (): ParcelFields => {
    const value = (field: ParcelField): string => fields.get(field)?.control.value ?? '';
    return {
      weight: value('weight'), length: value('length'), width: value('width'), height: value('height'),
      originCountry: value('originCountry'), originOther: value('originOther'), originPostcode: value('originPostcode'),
      destinationCountry: value('destinationCountry'), destinationOther: value('destinationOther'),
      destinationPostcode: value('destinationPostcode'), serviceLevel: value('serviceLevel'),
    };
  };

  element.addEventListener('submit', (event) => {
    event.preventDefault();
    onSubmit(values());
  });

  const clearErrors = (): void => {
    for (const field of fields.values()) {
      field.setError(undefined);
    }
    summary.replaceChildren();
  };

  return {
    element,
    values,
    clearErrors,
    showErrors(errors, unplaced) {
      clearErrors();
      const messages = new Map(Object.entries(errors));
      fields.forEach((control, field) => control.setError(messages.get(field)));
      showErrorSummary(summary, [
        ...orderedErrors(errors).map((error) => ({ text: error.message, targetId: id(error.field) })),
        ...unplaced.map((message) => ({ text: message })),
      ]);
    },
    setPending(pending) {
      submit.disabled = pending;
      submit.textContent = pending ? 'Getting offers...' : 'Get offers';
    },
  };
}
