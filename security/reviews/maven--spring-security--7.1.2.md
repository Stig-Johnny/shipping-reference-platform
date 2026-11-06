---
component: "Spring Security 7.1.2 (org.springframework.security:spring-security-core, -config, -web, -crypto, -test) as managed by Spring Boot 4.1.2, via org.springframework.boot:spring-boot-starter-security:4.1.2 and org.springframework.boot:spring-boot-security-test:4.1.2"
kind: maven
version: "7.1.2"
verdict: APPROVE-WITH-CONDITIONS
scope: "apps/shipping-api only. Runtime classpath: spring-boot-starter-security 4.1.2, spring-boot-security 4.1.2 and spring-security-core/config/web/crypto 7.1.2. Test classpath: spring-security-test 7.1.2 and spring-boot-security-test 4.1.2. Not libs/c4-contract-kit."
conditions: ["Exact versions as resolved by Boot 4.1.2's BOM (spring-security.version 7.1.2), locked and SHA-256 verified; any Boot or Security bump is a new review", "Test side uses spring-security-test 7.1.2 (+ spring-boot-security-test 4.1.2) directly, never spring-boot-starter-security-test, which pulls spring-boot-starter-test (Mockito, XMLUnit, Awaitility, JSONassert, json-path) outside this review", "An explicit SecurityFilterChain authenticates every API route and denies anything unmatched, and the app defines its own UserDetailsService/AuthenticationManager so Boot's generated user and WARN-logged password never exist; CI fails if 'Using generated security password' appears in any log", "Tenant identity comes only from the authenticated principal's server-side membership, never from a header or parameter; tests cover 401 unauthenticated, foreign-tenant read and write, and missing membership", "CSRF protection stays on for the browser SPA (`csrf.spa()`); any `ignoringRequestMatchers` or `disable()` carries a recorded reason", "Passwords via `PasswordEncoderFactories.createDelegatingPasswordEncoder()`; no `{noop}`, `NoOpPasswordEncoder` or `User.withDefaultPasswordEncoder()` outside test fixtures; demo credentials are synthetic", "No `AesBytesEncryptor` or `Encryptors.standard/stronger/text/delux` (deprecated by CVE-2026-47842)", "No other spring-security-* module (oauth2-*, saml2, ldap, acl, cas, webauthn, messaging) without a new review; spring-aop/spring-web 7.0.10, spring-boot 4.1.2 and micrometer-observation 1.17.2 carry their own allowlist entries"]
reviewer: "AI subagent (Claude Opus 5.5), under the repository owner's delegation of 2026-11-06"
reviewedAt: "2026-11-06"
expiresAt: "2027-05-06"
---

# Security review: Spring Security 7.1.2 as managed by Spring Boot 4.1.2

**Method.** On 2026-11-06 the following were read through WebFetch: the Boot 4.1.2 BOM and starter POMs on Maven Central, every Spring Security 7.1.2 POM, spring.io/security and its advisory pages, the GitHub release pages, the Spring docs, deps.dev, OSV and Scorecard. Nothing was downloaded or run. `api.github.com` refused the fetches (HTTP 403).

## 1. Verdict
**APPROVE-WITH-CONDITIONS**, for the runtime and test classpaths of apps/shipping-api.
- Both versions are current. 7.1.2 is the newest 7.1.x and 4.1.2 the newest 4.1.x, and both 7.2 and 4.2 are still release candidates ([Security metadata](https://repo1.maven.org/maven2/org/springframework/security/spring-security-core/maven-metadata.xml), [Boot metadata](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml)).
- No published advisory affects them (§5).
- The risk is in configuration, not in the library. Boot's defaults (a generated, logged password), CSRF, password storage and tenant derivation are what decide whether "authenticated tenant membership" holds, so the conditions pin those.
- One dependency choice is rejected: Boot 4's `spring-boot-starter-security-test`, because it brings the whole test starter.

## 2. What it is and why it is needed
The blueprint (§3) says: "Tenant identity comes from authenticated membership, never a trusted request header." Spring Security is Boot's standard authentication and authorisation layer. `spring-security-test` supplies `@WithMockUser` and MockMvc post-processors for the tenant-denial tests ([test docs](https://docs.spring.io/spring-security/reference/servlet/test/index.html)). v01-design §4 limits Boot to the example app.

## 3. Provenance
- **Managed versions.** Boot 4.1.2's BOM sets `spring-security.version` 7.1.2 and `spring-framework.version` 7.0.10, and imports `spring-security-bom` ([BOM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom)).
- **Release dates.** Security 7.1.2: 22 Oct, commit `e178580` ([release](https://github.com/spring-projects/spring-security/releases/tag/7.1.2)). Boot 4.1.2: 22 Oct, commit `634e6fd`, "Upgrade to Spring Security 7.1.2 #52137" ([release](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2)). Central files are dated 2026-10-22.
- **Build.**
  - GitHub Actions, with every `uses:` pinned by SHA.
  - The release-train build runs with `permissions: contents: read` and hands off to another repository for the final publish, which was not identified.
  - Snapshot CI signs with GPG secrets.
  - No provenance step, and deps.dev shows `slsaProvenances: []` ([workflows](https://github.com/spring-projects/spring-security/tree/main/.github/workflows)).
- **Central files.** Every file has `.asc`, `.sha256` and `.sha512` ([listing](https://repo1.maven.org/maven2/org/springframework/security/spring-security-core/7.1.2/)).
- **SHA-256, from the publisher's `.jar.sha256` sidecars:**

| Artifact | SHA-256 |
|---|---|
| spring-security-core-7.1.2.jar | `39df3dc77436d3e9308f89056c1a42605e08f0ad9ad208f7e34c4b16102886e1` |
| spring-security-config-7.1.2.jar | `826af4e79495d54b761a5f0313774a07d36359e83dd5518d57d69bacd7bbf478` |
| spring-security-web-7.1.2.jar | `fc36d37695462dbe4fb32bd6820043afbf83ebcffce4974929bf79a29fcb5e73` |
| spring-security-crypto-7.1.2.jar | `4aaa60bb612d5fece004c07f893b3a8d707c207bbd8585d10e0015b5301cd864` |
| spring-security-test-7.1.2.jar | `be0b650464a373c766d65a45ce7a03dbaafa4745b4724b33c526bff6735e2448` |
| spring-boot-starter-security-4.1.2.jar (sidecar, equal to the signed [.module](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-security/4.1.2/spring-boot-starter-security-4.1.2.module)) | `66756863f254e4393dd7f0148d37679034548fd573031082c226be498773f611` |
| spring-boot-security-4.1.2.jar | `12b5c8478a965677aaa98ced147d15db214cfeb373eb5acfd84c1e9c25dc8ac3` |
| spring-boot-security-test-4.1.2.jar | `8d8394bbc3bf985cae0e91201201692f758eed6de3805ffefc107bae621c9a55` |
| spring-boot-starter-security-test-4.1.2.jar (**not approved**, recorded so the gate can block it) | `4c20fd0a0deae3e2bb4f82eaa5701423de9b695a3bcddeab22c43768040a794f` |

## 4. Maintenance and adoption
- **Scorecard 7.1** (2026-11-02; [scorecard](https://api.securityscorecards.dev/projects/github.com/spring-projects/spring-security)):
  - 10: Maintained, Security-Policy, Fuzzing (OSS-Fuzz), Pinned-Dependencies, Token-Permissions, Dangerous-Workflow.
  - **Code-Review 0**, Branch-Protection 3, SAST 0.
- **Usage.** 9,667 stars and 6,389 forks ([deps.dev](https://api.deps.dev/v3/projects/github.com%2Fspring-projects%2Fspring-security)).
- **Reporting.** Vulnerabilities go through GitHub's private advisory form ([policy](https://spring.io/security-policy)).
- **Support.** The OSS support end date for 7.1.x was not retrieved (§11).

## 5. Known vulnerabilities at 7.1.2
**None found on 2026-11-06.**
- **deps.dev.** `advisoryKeys: []` for spring-security-core, -web, -config, -crypto and -test 7.1.2, spring-boot-starter-security 4.1.2, and spring-web/spring-webmvc 7.0.10 ([core](https://api.deps.dev/v3/systems/maven/packages/org.springframework.security%3Aspring-security-core/versions/7.1.2)).
- **spring.io.** The 25 newest advisories are all dated 2026-08-20 ([list](https://spring.io/security)). The Spring Security ones affect 7.1.0 only within 7.1.x, and are fixed in 7.1.1: CVE-2026-41707, -47841, -47842, -47877, -59276 and -59277 (e.g. [CVE-2026-41707](https://spring.io/security/cve-2026-41707)).
- **Spring Framework.** The 2026-08-20 Framework advisories affect 7.0.0–7.0.8 and are fixed in 7.0.9, so 7.0.10 is outside them.
- **Spring Boot.** No Boot advisory appears in the visible list.
- **OSV.** The osv.dev name search returned nothing, which is inconclusive ([OSV](https://osv.dev/list?ecosystem=Maven&q=org.springframework.security)).
- **Carry-over from CVE-2026-47842.** `AesBytesEncryptor` and `Encryptors.*` are deprecated, and data they encrypted may need re-encryption ([CVE-2026-47842](https://spring.io/security/cve-2026-47842)). Hence condition 7.

## 6. License
Apache-2.0, in every Spring Security 7.1.2 POM and on deps.dev ([POM](https://repo1.maven.org/maven2/org/springframework/security/spring-security-core/7.1.2/spring-security-core-7.1.2.pom)).

## 7. Execution privileges
- **Runtime** (apps/shipping-api).
  - **Every request.** A servlet filter chain runs on every request. It reads headers, cookies and the session, and creates sessions and CSRF tokens. The default CSRF repository is `HttpSessionCsrfTokenRepository`, and protection covers unsafe methods by default ([CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)).
  - **Method security** uses Spring AOP proxies and evaluates SpEL expressions from `@PreAuthorize`. Boot does not enable it by default, and "unannotated methods are not secured" ([method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)).
  - **Password hashing** (BCrypt through `DelegatingPasswordEncoder`) costs CPU per login ([password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)).
- **Boot auto-configuration.** It secures every endpoint, including `/error` and actuator. It creates user `user` with a random password "printed at WARN level", which is "for development use only". It backs off only when a `UserDetailsService`, `AuthenticationProvider` or `AuthenticationManager` bean exists ([Boot docs](https://docs.spring.io/spring-boot/reference/web/spring-security.html)). Our CI logs are retained artifacts, so condition 3 applies.
- **Network and processes.** The modules approved here start no process. Outbound network use (JWK sets, LDAP, SAML) lives in the out-of-scope modules. This is inferred from the module split, not audited.
- **Test.** spring-security-test sets the `SecurityContext` and adds MockMvc support, on the test classpath only.

## 8. Direct dependencies as published

| Artifact | Direct dependencies (all compile) |
|---|---|
| [spring-boot-starter-security 4.1.2](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-security/4.1.2/spring-boot-starter-security-4.1.2.pom) | spring-boot-starter 4.1.2, spring-boot-security 4.1.2, spring-aop 7.0.10 |
| [spring-boot-security 4.1.2](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-security/4.1.2/spring-boot-security-4.1.2.pom) | spring-boot 4.1.2, spring-security-config 7.1.2, spring-security-web 7.1.2 |
| spring-security-core 7.1.2 | spring-security-crypto 7.1.2; spring-aop/beans/context/core/expression 7.0.10; **micrometer-observation 1.17.2** (flag: its own entry) |
| spring-security-config / -web 7.1.2 | spring-security-core; Spring Framework 7.0.10 modules (web adds spring-web) |
| spring-security-crypto 7.1.2 | only assertj-core 3.27.7, `runtime`, `optional` (not resolved) |
| spring-security-test 7.1.2 | spring-security-core, -web; spring-core, spring-test 7.0.10 |
| [spring-boot-security-test 4.1.2](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-security-test/4.1.2/spring-boot-security-test-4.1.2.pom) | spring-boot-test-autoconfigure 4.1.2, spring-boot-security 4.1.2, spring-security-test 7.1.2 |
| [spring-boot-starter-security-test 4.1.2](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-security-test/4.1.2/spring-boot-starter-security-test-4.1.2.pom) (**not approved**) | adds spring-boot-starter-test, which brings mockito-core 5.23.0, xmlunit, awaitility, jsonassert, json-path and more ([POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-test/4.1.2/spring-boot-starter-test-4.1.2.pom)) |

`spring-boot-starter` brings logging, autoconfigure, jakarta.annotation-api 3.0.0 and **snakeyaml 2.6** ([POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter/4.1.2/spring-boot-starter-4.1.2.pom)). These belong to the Boot core review.

## 9. Alternatives considered
- A hand-written servlet filter checking a session or token: no framework, but everything Spring Security already gets right (CSRF, session fixation, password storage) would be redone by hand.
- An OAuth2 resource server with an external identity provider. That is deferred to production identity (blueprint §10, "Later").

## 10. Conditions and residual risk
**Conditions:** as in the frontmatter.

**Residual risk:**
- Correctness rests on our configuration and on the tenant tests, not on the library.
- Upstream shows Code-Review 0 on Scorecard, and there is no build provenance.
- Our version lags as soon as 7.1.3 ships. Expiry and the re-review rule cover that.
- BCrypt login cost is a DoS lever if login is exposed without rate limiting. The demo is local and private.

## 11. Not verified
- No jar was downloaded or hashed, and the `.asc` signatures were not checked. The SHA-256 values are Central sidecars.
- spring.io advisories older than the visible 25, all dated 2026-08-20, were not seen.
- The 7.1.x support end date.
- The repository that performs the final publish to Central.
- What `csrf.spa()` configures internally.
- What Boot's test auto-configuration adds for MockMvc security.
- OSV's `POST /v1/query` was not used. deps.dev (fed by OSV) stands in.
