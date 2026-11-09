// ShipmentDrawer: a native modal <dialog> over the shipments table, so the rest of the page is inert and focus stays
// inside. It is labelled by its heading, which receives focus on open; Escape closes it. "Download label (SVG)" fetches
// the label as a blob and saves it under the file name from Content-Disposition; the SVG is never shown inline. The
// object URL is revoked when it is replaced, on close, and so on user switch, which closes every screen.

import type { ApiClient } from '../../api/client.ts';
import { failureMessage, failureTitle } from '../../api/messages.ts';
import { isRetryable, problemType, referenceOf } from '../../api/problems.ts';
import { button, emptyState, loadingText, notice, problemAlert, skeleton, statusBadge } from '../../ui/components.ts';
import { el, replace, visuallyHidden } from '../../ui/dom.ts';
import {
  canDownloadLabel, drawerDetails, drawerHeading, drawerLoaded, labelMessage, labelSettled,
} from './ShipmentDrawerModel.ts';
import type { DrawerLoad, LabelState } from './ShipmentDrawerModel.ts';

export interface DrawerOptions {
  readonly api: ApiClient;
  readonly announce: (message: string) => void;
  /** Close and Escape ask the page to leave the drawer's route; the page then calls close(). */
  readonly onClose: () => void;
  readonly shipmentsPath: string;
}

export interface DrawerView {
  close(): void;
}

export function openShipmentDrawer(host: HTMLElement, shipmentId: string, options: DrawerOptions): DrawerView {
  const controller = new AbortController();
  let closed = false;
  let load: DrawerLoad = { kind: 'loading' };
  let label: LabelState = { kind: 'idle' };
  let objectUrl: string | undefined;

  const heading = el('h2', { id: 'drawer-heading', class: 'drawer__heading', tabindex: '-1' }, 'Shipment');
  const content = el('div', { class: 'drawer__body' });
  const labelArea = el('div', { class: 'drawer__label' });
  const downloadButton = button('Download label (SVG)', 'primary', () => {
    if (label.kind !== 'downloading') void download();
  });
  const closeButton = button('Close', 'secondary', options.onClose);
  const actions = el('div', { class: 'button-row drawer__actions' }, downloadButton, closeButton);
  const dialog = el('dialog', { class: 'drawer', 'aria-labelledby': 'drawer-heading' },
    el('div', { class: 'drawer__header' }, heading), content, labelArea, actions);

  const revoke = (): void => {
    if (objectUrl !== undefined) {
      URL.revokeObjectURL(objectUrl);
      objectUrl = undefined;
    }
  };

  const save = (blob: Blob, fileName: string): void => {
    revoke();
    objectUrl = URL.createObjectURL(blob);
    const link = el('a', { href: objectUrl, download: fileName, hidden: true });
    dialog.append(link);
    link.click();
    link.remove();
  };

  const download = async (): Promise<void> => {
    label = { kind: 'downloading' };
    render();
    const result = await options.api.getShipmentLabel(shipmentId, controller.signal);
    if (closed || (!result.ok && result.failure.kind === 'aborted')) {
      return;
    }
    label = labelSettled(result);
    if (result.ok) {
      save(result.value.blob, result.value.fileName);
      options.announce(labelMessage(label) ?? 'Label ready. Check your browser downloads.');
    }
    render();
  };

  const renderLabel = (): void => {
    const message = labelMessage(label);
    if (label.kind === 'ready' && objectUrl !== undefined && message !== undefined) {
      replace(labelArea, notice('success', el('p', {}, message, ' ',
        el('a', { href: objectUrl, download: label.fileName }, 'Download again'))));
    } else if (label.kind === 'failed' && message !== undefined) {
      const failure = label.failure;
      replace(labelArea, problemAlert({
        title: failureTitle(failure), message, reference: referenceOf(failure),
        onRetry: isRetryable(failure) ? () => void download() : undefined,
      }));
    } else {
      replace(labelArea);
    }
  };

  const render = (): void => {
    heading.textContent = drawerHeading(load);
    switch (load.kind) {
      case 'loading':
        content.setAttribute('aria-busy', 'true');
        replace(content, loadingText('the shipment'),
          skeleton('line'), skeleton('line'), skeleton('line'), skeleton('line'));
        break;
      case 'not-found':
        content.setAttribute('aria-busy', 'false');
        replace(content, emptyState('Shipment not found.', { label: 'All shipments', href: options.shipmentsPath }));
        break;
      case 'failed': {
        content.setAttribute('aria-busy', 'false');
        const failure = load.failure;
        replace(content, problemAlert({
          title: failureTitle(failure), message: failureMessage(failure), reference: referenceOf(failure),
          onRetry: isRetryable(failure) ? fetchShipment : undefined,
        }));
        break;
      }
      case 'loaded': {
        content.setAttribute('aria-busy', 'false');
        const list = el('dl', { class: 'details' });
        for (const detail of drawerDetails(load.shipment)) {
          const value = el('dd', { class: detail.code === true ? 'mono details__code' : undefined });
          if (detail.tone !== undefined) {
            value.append(statusBadge(detail.lines.join(' '), detail.tone));
          } else if (detail.spoken !== undefined) {
            value.append(el('span', { 'aria-hidden': 'true', class: 'num' }, detail.lines.join(' ')),
              visuallyHidden(detail.spoken));
          } else {
            detail.lines.forEach((line, index) => {
              if (index > 0) {
                value.append(el('br', {}));
              }
              value.append(line);
            });
          }
          list.append(el('dt', {}, detail.term), value);
        }
        replace(content, list);
        break;
      }
    }
    const downloading = label.kind === 'downloading';
    downloadButton.hidden = !canDownloadLabel(load);
    downloadButton.textContent = downloading ? 'Downloading label...' : 'Download label (SVG)';
    // Keep the focused control mounted while refusing repeat activation during the request.
    downloadButton.setAttribute('aria-disabled', String(downloading));
    renderLabel();
  };

  function fetchShipment(): void {
    load = { kind: 'loading' };
    render();
    void options.api.getShipment(shipmentId, controller.signal).then((result) => {
      const ignored = !result.ok
        && (result.failure.kind === 'aborted' || problemType(result.failure) === 'unauthenticated');
      if (closed || ignored) {
        return;
      }
      load = drawerLoaded(result);
      render();
    });
  }

  dialog.addEventListener('cancel', (event) => {
    event.preventDefault();
    options.onClose();
  });
  // A browser may close the dialog itself on Escape instead of letting the page cancel it.
  dialog.addEventListener('close', () => {
    if (!closed) {
      options.onClose();
    }
  });
  host.append(dialog);
  dialog.showModal();
  heading.focus();
  fetchShipment();

  return {
    close() {
      if (closed) {
        return;
      }
      closed = true;
      controller.abort();
      revoke();
      dialog.close();
      dialog.remove();
    },
  };
}
