# The SIGNA oblivious pseudonym layer

How SIGNA links a customer across member banks without any party learning who
that customer is.

- **Protocol**: RFC 9497 VOPRF (verifiable mode, `0x01`)
- **Ciphersuite**: `P256-SHA256`
- **Hash to curve**: RFC 9380 `P256_XMD:SHA-256_SSWU_RO_`
- **Proof system**: batched Chaum–Pedersen DLEQ, Fiat–Shamir transformed

---

## 1. What problem this solves

Two banks want to know whether they are dealing with the same person, so that a
customer blocked for fraud at one is flagged at the other. Neither may disclose
its customer list. The central server must not accumulate one either.

Before this layer, the server stored the raw FIN of every customer of every
member bank in `customers.hash` and matched on it directly. Whatever the
architecture diagram said, the deployed system was a national identity database
with a fraud feature attached: a single database compromise exposed the FIN of
every customer in the federation.

The OPRF replaces that. Each bank derives a **pseudonym** for its customer by
running a two-party protocol with the server:

- the bank never reveals the identifier, because it is blinded before it leaves;
- the server never reveals its key, because the bank only ever sees the key
  applied to blinded points;
- the pseudonym is nevertheless **identical** at every bank for the same person,
  which is what makes matching work.

## 2. The protocol

Write `P = HashToCurve(id)`, `k` for the server key, `G` for the generator, and
`pkS = k·G` for the published public key.

```
BANK                                       SERVER
────────────────────────────────────────────────────────────────────────
id  ──► canonicalize                       holds k, publishes pkS = k·G
        P = HashToCurve("az-fin:7GK4M2Q")
        r ←$ [1, n-1]                      ← r is fresh per evaluation
        B = r·P
                        ── B ────────────►
                                           validate B is on the curve,
                                           not the identity
                                           E = k·B
                                           π = DLEQ{ log_G(pkS) = log_B(E) }
                        ◄── E, π ─────────
        verify π against pkS
        N = r⁻¹·E  =  r⁻¹·k·r·P  =  k·P    ← the blind cancels exactly
        pseudonym = SHA-256(len(id)‖id‖len(N)‖N‖"Finalize")
```

Three properties fall out:

| Property | Mechanism |
|---|---|
| The server cannot learn the identifier | `r` is uniform, so `B` is uniform in the group and independent of `P` |
| The bank cannot learn the key | It only ever observes `k` applied to points it chose; recovering `k` is the discrete log problem |
| The pseudonym is the same everywhere | `r` cancels, leaving `k·P`, which depends only on the identifier and the key |

### Why the proof is not optional

Without `π`, a malicious server can answer bank A under key `k₁` and bank B
under key `k₂`. Both banks work fine in isolation and never match each other,
while the server — knowing both keys — can link everything. The federation
believes it is sharing intelligence and is in fact only feeding a central
observer. The DLEQ proof shows that the key used on the response is the key
behind the published `pkS`, so a bank pinning `pkS` detects this immediately.

Proofs are **batched**: one proof covers a whole request. The verifier folds the
batch into a random linear combination with coefficients derived from the
transcript, so cheating on any single element fails the proof for the batch.

## 3. Code map

| Concern | Type |
|---|---|
| Suite constants, transcript encoding | `crypto/Oprf.java` |
| Group operations, element and scalar validation | `crypto/P256Group.java` |
| RFC 9380 hash to curve | `crypto/HashToCurve.java` |
| DLEQ proving and verification | `crypto/Dleq.java`, `crypto/DleqProof.java` |
| Server half | `crypto/VoprfServer.java` |
| Bank half (reference implementation) | `crypto/VoprfClient.java` |
| Identifier canonicalisation | `crypto/PiiNormalizer.java` |
| Key ring, rotation | `crypto/key/` |
| Endpoint, quotas, audit | `service/OprfService.java`, `service/OprfRateLimiter.java` |

`VoprfClient` lives here so the reference implementation and the server cannot
drift apart, and so the RFC vectors exercise both halves. **It belongs in the
bank connector**, not on this server.

## 4. Enrolling a customer

```bash
# 1. Fetch and pin the server's public parameters (once, at deploy time).
curl https://<host>/api/v1/oprf/public-key

# 2. Blind locally, then evaluate. The identifier never leaves the bank.
curl -X POST https://<host>/api/v1/oprf/evaluate \
  -H "X-Signa-Client-Id: bank_1a2b3c4d5e6f7a8b" \
  -H "X-Signa-Api-Key: signa_sk_..." \
  -H "Content-Type: application/json" \
  -d '{"blindedElements":["02dd0590...","03462e9a..."]}'

# 3. Verify the proof, unblind, then enrol with the pseudonym.
curl -X POST https://<host>/api/v1/customers \
  -H "Authorization: Bearer <admin jwt>" \
  -d '{"name":"...","pseudonym":"0412e8f7...","oprfKeyId":"a1b2c3d4e5f60718","bankId":"..."}'
```

`POST /api/v1/customers` no longer accepts a `fin` field. It validates that
`pseudonym` is exactly 64 hex characters, so a caller that skipped the OPRF and
tried to send a raw identifier is rejected rather than silently storing
plaintext in the pseudonym column.

### Canonicalisation

The OPRF is a function of exact bytes. `7gk4m2q` and `7GK4M2Q` produce unrelated
pseudonyms. If two banks disagree on normalisation, the same customer enrols
twice and never matches — a failure with no error and no symptom except fraud
slipping through. `PiiNormalizer` is the single definition: NFKC, strip
whitespace and separators, upper-case in the invariant locale, prefix the
identifier domain. Every connector must apply it identically.

## 5. Threat model

### Holds

- **A curious server.** Blinded elements are uniform in the group. The server
  learns that a bank asked, when, and how many — nothing about whom.
- **A curious bank.** It sees `k·P` for identifiers it already holds. It cannot
  recover `k`, and cannot compute a pseudonym for an identifier it never
  submitted, except through the rate-limited endpoint.
- **A cheating server.** The DLEQ proof catches key substitution and key
  partitioning.
- **Database disclosure.** `customers` holds pseudonyms, not identifiers. An
  attacker with the dump alone cannot invert them.
- **Malformed input.** Every point is validated before the key touches it, which
  closes the invalid-curve and small-subgroup routes to key recovery.

### Does not hold

These are real, and worth stating plainly rather than leaving for a reviewer to
find:

1. **The server can brute-force its own database.** It holds `k`, so it can
   compute `pseudonym(candidate)` for any guess and compare. Azerbaijani FIN
   codes are seven characters — a space small enough to exhaust. The OPRF
   protects the identifier *in transit and at the banks*; it does not make the
   stored pseudonym irreversible to the key holder. Mitigations, in increasing
   order of work: keep `k` in an HSM or KMS so no operator ever sees it; split
   `k` across a threshold of parties so no single operator can evaluate;
   distribute it across institutions so collusion is required. **The right next
   step is threshold key holding**, and until it exists the honest claim is
   "the server cannot see identifiers in transit and cannot read them from a
   stolen database", not "the server can never learn who a pseudonym is".

2. **A member bank can enumerate, slowly.** Any bank can push candidate
   identifiers through the oracle and match the results against pseudonyms it
   has seen. The rate limiter and the audit table are the only defences, which
   is why they are on by default and why disabling them is a deliberate act.

3. **Pseudonyms are personal data.** A stable cross-institution identifier for a
   natural person is personal data under GDPR and under Azerbaijani data
   protection law. Pseudonymisation reduces risk; it is not anonymisation. Treat
   `customers.pseudonym` accordingly — it is deliberately absent from every read
   API and from all logging.

4. **Rate limit counters are per process.** Behind more than one replica each
   enforces its own share and the effective limit multiplies. Move the buckets
   to Redis before scaling out.

## 6. Key management

`SIGNA_OPRF_ACTIVE_KEY` is a P-256 scalar in 64 hex characters. There is no
default and the application refuses to start without it, except under the `dev`
profile, where it derives a key from a seed committed to this repository and logs
a warning that the key is public.

### A fresh clone needs no configuration

`./gradlew bootRun` activates `dev,local` (see `build.gradle`), so a clone runs
with an in-memory database, the published development OPRF key, the published
development JWT key in `application-dev.yaml`, and a seeded operator account.
Nothing has to be set up, and nothing in that path is secret.

`bootJar` is deliberately left alone. The packaged artifact carries no default
profile, so a deployment picks up none of those values and still fails fast
without real key material:

    Could not resolve placeholder 'JWT_SECRET_KEY'

That split is the whole point. If the development keys also applied in
production, anyone holding this repository could mint a SUPER_ADMIN token and
recompute any pseudonym in the network.

Generate one with:

```bash
head -c 32 /dev/urandom | xxd -p -c 32
```

Key ids are **derived** from the public key (first 8 bytes of its SHA-256), not
configured. A hand-typed id could label two different keys identically, and the
platform would compare pseudonyms that can never match — indistinguishable from
"this person banks nowhere else".

### Rotation

Rotating `k` changes every pseudonym. A rotation is a re-enrolment of the whole
federation, not a config change:

1. Move the current key into `SIGNA_OPRF_PREVIOUS_KEYS` and set the new one as
   `SIGNA_OPRF_ACTIVE_KEY`. Both remain evaluable.
2. Banks re-enrol their customers under the new key id. Old rows stay matchable
   under the old id while this proceeds, because matching is scoped by key id.
3. Once every bank has migrated, delete the old rows and drop the retired key.

Rotate on suspected key compromise. Note that rotation does not undo a
compromise retroactively: an attacker who held the old key can still invert the
pseudonyms enrolled under it.

## 7. Member bank credentials

Banks authenticate with `X-Signa-Client-Id` and `X-Signa-Api-Key`, not with an
admin JWT. An admin token carries no bank identity, so an evaluation made under
one could be neither attributed nor rate limited.

Credentials are issued with the bank (`POST /api/v1/banks`) and returned once.
Only a SHA-256 digest is stored. `POST /api/v1/banks/{id}/rotate-api-key`
replaces a key; `PUT /api/v1/banks/{id}/oprf-access?enabled=false` cuts a bank
off without deleting its records or its audit trail.

## 8. Tests

`VoprfRfc9497VectorTest` replays the published RFC 9497 Appendix A.3.2 vectors
byte for byte — key derivation, blinding, evaluation, proofs and outputs, at
batch sizes 1 and 2. This is the evidence that an independent implementation in
another language reaches the same pseudonym. If it fails, interoperability is
broken and every enrolled pseudonym is at risk of becoming unmatchable.

`OprfHardeningTest` covers what must be refused: the identity element, off-curve
points, malformed encodings, unreduced scalars, tampered and substituted proofs,
reordered batches, and proofs from a rogue key.

```bash
./gradlew test --tests "teknofest.signa.producer.crypto.*"
```

## 9. Development-only local client

`application.security.oprf.local-client.enabled=true` exposes
`POST /api/v1/oprf/local/derive`, which accepts a **raw identifier** and runs
both halves of the protocol on the server. It exists so the back office can be
demonstrated before the bank connectors are written.

It defeats the privacy property. It is off by default, restricted to
`SUPER_ADMIN`, refuses to start under the `prod` profile, and logs a warning
banner whenever it is on.
