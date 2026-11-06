# Security policy

## Reporting a vulnerability

Use GitHub's private vulnerability reporting on this repository: open the Security tab and choose "Report a
vulnerability". Never report a vulnerability in a public issue, pull request or discussion.

## Scope

In scope:

- the dependency policy checker, `tools/policy/PolicyCheck.java`;
- the CI workflows in `.github/workflows/`;
- the published dependency reviews in `security/reviews/` and the allowlist, `security/allowlist.json`;
- the demo code.

## Support

This is a reference and demo repository. It has no production deployment. Support is best-effort and covers the
demo only.

## Intentional fixtures

The demo bearer tokens in `docs/modules/tenants.md` are public, guessable test fixtures, not secrets. They are
intentional and are not a vulnerability.
