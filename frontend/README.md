# Signa console

The operations console for the SIGNA fraud intelligence network. React and Vite,
talking to the `producer` service.

```bash
npm install
npm run dev          # http://localhost:5173, proxying /api to :9090
```

Point it at a producer somewhere other than `localhost:9090`:

```bash
SIGNA_API_URL=https://producer.example.az npm run dev
```

For a build served from a different origin, set `VITE_API_BASE_URL` instead and
add that origin to `CORS_ALLOWED_ORIGINS` on the producer.

## The part worth knowing about

`src/lib/oprf.js` is the **bank half of the privacy layer**, running in the
browser. When an operator enrols a customer, the identifier they type is
canonicalised, mapped onto a P-256 point and multiplied by a random blind
before anything is sent. What leaves the tab is a curve point. The server
multiplies it by a key it never reveals, returns a zero-knowledge proof that it
used the key it publishes, and the browser checks that proof before unblinding.

This matters because the alternative — posting the FIN to a server endpoint and
letting it do the work — would leave the whole protocol as decoration. The
identifier is never in a request body, never in a log, never at rest on the
server.

The enrolment drawer shows each step as it happens, and the result screen shows
both the value that was sent and the value that never was. That is deliberate:
someone handling other people's national ID numbers should be able to see where
they go.

### Verifying it

```bash
npm run verify:oprf
```

Two suites, both of which must pass before trusting a change to `oprf.js`:

- **`scripts/oprf.vectors.mjs`** replays the published RFC 9497 Appendix A.3.2
  test vectors (VOPRF, `P256-SHA256`) — key handling, hash-to-curve, blinding,
  evaluation, DLEQ verification and the final output, at batch sizes one and
  two, plus the rejections: off-curve points, the identity, unreduced scalars,
  reordered batches, and evaluations under an unpublished key.
- **`scripts/oprf.parity.mjs`** pins canonicalisation against the same fixtures
  as `PiiNormalizerTest` on the Java side.

The Java server is checked against the *same published vectors*, independently.
Neither implementation is checked against the other, so neither can drag the
other off-spec. If the console and the server ever disagreed about a single
byte, the same customer would enrol under two unrelated pseudonyms and the
network would silently stop matching them — no error, no log line, just fraud
getting through.

## Credentials

Two, kept apart on purpose.

**The operator's JWT** identifies a person and opens the back office. Held in
`localStorage`.

**The member bank's API key** identifies an institution and is the only thing
that opens `/api/v1/oprf/evaluate`. Held in `sessionStorage`, so it dies with
the tab, and attached from the Member banks screen. It is never sent alongside
the operator token: an evaluation attributed to a person rather than an
institution could be neither rate limited nor audited, and the quotas are what
stop the OPRF being used as a lookup table for the national identifier space.

## Layout

```
src/
  lib/oprf.js        the protocol, browser side
  lib/api.js         transport, and the credential separation above
  lib/session.jsx    who is signed in, and the pinned server key
  ui/kit.jsx         buttons, tables, sheets, toasts
  ui/Shell.jsx       sidebar and frame
  pages/             one file per screen
scripts/             the two verification suites
```

## Design

Warm white paper, ink text, one serif for display and Inter for everything else.
Colour carries meaning rather than decoration: if something is red it is
blocked, if it is amber the network suspended it. Hairlines instead of drop
shadows, tabular numerals in every table, and no icon set — the labels say what
they mean.
