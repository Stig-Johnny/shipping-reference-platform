// /sign-in: the synthetic user picker. Continue holds demo-<handle> in memory and confirms it with
// getCurrentMembership.

import { button, notice, problemAlert, showErrorSummary } from '../ui/components.ts';
import { el, replace } from '../ui/dom.ts';
import { signInMessage } from './session.ts';
import type { Auth, Session } from './session.ts';
import { SYNTHETIC_USERS } from './users.ts';

export interface SignInScreen {
  readonly heading: HTMLElement;
  dispose(): void;
}

export interface SignInOptions {
  readonly auth: Auth;
  /** Why the user is here again, such as "Sign in again." after the server refused the token. */
  readonly message?: string | undefined;
  readonly onSignedIn: (session: Session) => void;
}

export function mountSignIn(container: HTMLElement, options: SignInOptions): SignInScreen {
  let disposed = false;
  const viewController = new AbortController();
  const heading = el('h1', { id: 'sign-in-heading', tabindex: '-1' }, 'Sign in');
  const summary = el('div', {});
  const alert = el('div', {});
  const choices = el('fieldset', { class: 'choice-list' },
    el('legend', { class: 'choice-list__legend' }, 'Synthetic user'));
  for (const user of SYNTHETIC_USERS) {
    const id = `user-${user.handle}`;
    choices.append(el('div', { class: 'choice' },
      el('input', { type: 'radio', name: 'user', id, value: user.handle, 'aria-describedby': `${id}-hint` }),
      el('label', { for: id, class: 'choice__label' }, user.displayName),
      el('p', { class: 'choice__hint', id: `${id}-hint` }, user.description)));
  }
  const submit = button('Continue', 'primary', undefined, 'submit');
  const form = el('form', { class: 'form form--narrow', novalidate: true, 'aria-labelledby': 'sign-in-heading' },
    summary, alert, choices, el('div', { class: 'button-row' }, submit));

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const chosen = form.querySelector<HTMLInputElement>('input[name="user"]:checked');
    if (chosen === null) {
      const first = `user-${SYNTHETIC_USERS[0]?.handle ?? ''}`;
      showErrorSummary(summary, [{ text: 'Choose a synthetic user to continue.', targetId: first }]);
      return;
    }
    replace(summary);
    replace(alert);
    submit.disabled = true;
    submit.textContent = 'Signing in...';
    void options.auth.signIn(chosen.value, viewController.signal).then((outcome) => {
      if (disposed) {
        return;
      }
      if (outcome.kind === 'signed-in') {
        options.onSignedIn(outcome.session);
        return;
      }
      submit.disabled = false;
      submit.textContent = 'Continue';
      const message = signInMessage(outcome);
      if (message !== undefined) {
        replace(alert, problemAlert({ title: 'Not signed in', message }));
      }
    });
  });

  replace(container, el('section', { class: 'page page--narrow' },
    heading,
    el('p', {}, 'Choose one of the public demo users. They exist only with synthetic data, and their tokens are '
      + 'published test fixtures, not secrets.'),
    options.message === undefined ? null : notice('info', el('p', {}, options.message)),
    form));

  return {
    heading,
    dispose: () => {
      disposed = true;
      viewController.abort();
    },
  };
}
