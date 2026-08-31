/**
 * The bank's half of the SIGNA privacy layer, in the browser.
 *
 * RFC 9497 VOPRF, ciphersuite P256-SHA256, with RFC 9380 hash-to-curve
 * (P256_XMD:SHA-256_SSWU_RO_). This is the JavaScript counterpart of
 * teknofest.signa.producer.crypto in the producer service, and it is verified
 * against the same published test vectors: see scripts/oprf.vectors.mjs.
 *
 * Why it exists here rather than on the server: the customer identifier is
 * blinded before anything crosses the network. What leaves this file is a point
 * on a curve, and no amount of access to the request log recovers the FIN from
 * it. If the console called a server endpoint with the raw identifier instead,
 * the whole layer would be decoration.
 *
 * Side-channel note: BigInt arithmetic is not constant time, and the scalar
 * ladder below branches on key bits. In this setting the attacker would need to
 * be running code on the operator's own machine, at which point the identifier
 * on screen is the easier target. A hardware-backed connector should still
 * prefer a constant-time implementation.
 */

/* ------------------------------------------------------------------ *
 * Field and curve constants (NIST P-256 / secp256r1)
 * ------------------------------------------------------------------ */

const P = 0xffffffff00000001000000000000000000000000ffffffffffffffffffffffffn;
const N = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551n;
const A = P - 3n;
const B = 0x5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604bn;
const GX = 0x6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296n;
const GY = 0x4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5n;

/** Z = -10, RFC 9380 Section 8.2. */
const Z = P - 10n;

/** p = 3 (mod 4), so a square root is a single exponentiation. */
const SQRT_EXP = (P + 1n) >> 2n;

const ELEMENT_BYTES = 33;
const SCALAR_BYTES = 32;
const FIELD_ELEMENT_BYTES = 48;

/* ------------------------------------------------------------------ *
 * Byte helpers
 * ------------------------------------------------------------------ */

const ASCII = new TextEncoder();

export function bytesToHex(bytes) {
  let out = '';
  for (const byte of bytes) out += byte.toString(16).padStart(2, '0');
  return out;
}

export function hexToBytes(hex) {
  const clean = hex.trim();
  if (clean.length % 2 !== 0) throw new Error('hex string must have an even length');
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i += 1) {
    const byte = Number.parseInt(clean.slice(i * 2, i * 2 + 2), 16);
    if (Number.isNaN(byte)) throw new Error('hex string contains a non-hex character');
    out[i] = byte;
  }
  return out;
}

function concat(...parts) {
  const total = parts.reduce((sum, part) => sum + part.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}

const i2osp1 = (value) => Uint8Array.of(value & 0xff);
const i2osp2 = (value) => Uint8Array.of((value >>> 8) & 0xff, value & 0xff);

/** I2OSP(len(x), 2) || x — the length prefix that keeps transcripts unambiguous. */
const lp = (bytes) => concat(i2osp2(bytes.length), bytes);

function bytesToBigInt(bytes) {
  let value = 0n;
  for (const byte of bytes) value = (value << 8n) | BigInt(byte);
  return value;
}

function bigIntToBytes(value, length) {
  const out = new Uint8Array(length);
  let remaining = value;
  for (let i = length - 1; i >= 0; i -= 1) {
    out[i] = Number(remaining & 0xffn);
    remaining >>= 8n;
  }
  if (remaining !== 0n) throw new Error('value does not fit in the requested width');
  return out;
}

async function sha256(bytes) {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return new Uint8Array(digest);
}

/* ------------------------------------------------------------------ *
 * Modular arithmetic
 * ------------------------------------------------------------------ */

const mod = (value, m = P) => ((value % m) + m) % m;

function modPow(base, exponent, m) {
  let result = 1n;
  let b = mod(base, m);
  let e = exponent;
  while (e > 0n) {
    if (e & 1n) result = (result * b) % m;
    b = (b * b) % m;
    e >>= 1n;
  }
  return result;
}

/** inv0(x) = x^(p-2), which yields 0 for x = 0 exactly as RFC 9380 requires. */
const inv0 = (value) => modPow(value, P - 2n, P);

function modInverse(value, m) {
  const inverse = modPow(mod(value, m), m - 2n, m);
  if (mod(value, m) === 0n) throw new Error('cannot invert zero');
  return inverse;
}

/* ------------------------------------------------------------------ *
 * Point arithmetic, Jacobian coordinates
 *
 * Affine arithmetic would need a modular inversion per step, roughly four
 * hundred of them for one scalar multiplication. Jacobian coordinates defer
 * that to a single inversion at the end, which is the difference between an
 * enrolment feeling instant and feeling broken.
 * ------------------------------------------------------------------ */

const INFINITY = { x: 0n, y: 1n, z: 0n };
const GENERATOR = { x: GX, y: GY, z: 1n };

const isInfinity = (point) => point.z === 0n;

/** dbl-2001-b, specialised for a = -3. */
function double(point) {
  if (isInfinity(point) || point.y === 0n) return INFINITY;
  const { x, y, z } = point;

  const delta = mod(z * z);
  const gamma = mod(y * y);
  const beta = mod(x * gamma);
  const alpha = mod(3n * mod(x - delta) * mod(x + delta));

  const x3 = mod(alpha * alpha - 8n * beta);
  const z3 = mod(mod(y + z) * mod(y + z) - gamma - delta);
  const y3 = mod(alpha * mod(4n * beta - x3) - 8n * mod(gamma * gamma));

  return { x: x3, y: y3, z: z3 };
}

/** add-2007-bl. */
function add(p1, p2) {
  if (isInfinity(p1)) return p2;
  if (isInfinity(p2)) return p1;

  const z1z1 = mod(p1.z * p1.z);
  const z2z2 = mod(p2.z * p2.z);
  const u1 = mod(p1.x * z2z2);
  const u2 = mod(p2.x * z1z1);
  const s1 = mod(p1.y * p2.z * z2z2);
  const s2 = mod(p2.y * p1.z * z1z1);

  const h = mod(u2 - u1);
  const r = mod(2n * mod(s2 - s1));

  if (h === 0n) {
    // Same x. Either the same point, or a point and its negation.
    return r === 0n ? double(p1) : INFINITY;
  }

  const i = mod(mod(2n * h) * mod(2n * h));
  const j = mod(h * i);
  const v = mod(u1 * i);

  const x3 = mod(r * r - j - 2n * v);
  const y3 = mod(r * mod(v - x3) - 2n * mod(s1 * j));
  const z3 = mod(mod(mod(p1.z + p2.z) * mod(p1.z + p2.z) - z1z1 - z2z2) * h);

  return { x: x3, y: y3, z: z3 };
}

function scalarMultiply(point, scalar) {
  const k = mod(scalar, N);
  if (k === 0n || isInfinity(point)) return INFINITY;

  let result = INFINITY;
  let addend = point;
  let remaining = k;

  while (remaining > 0n) {
    if (remaining & 1n) result = add(result, addend);
    addend = double(addend);
    remaining >>= 1n;
  }
  return result;
}

const scalarMultiplyGenerator = (scalar) => scalarMultiply(GENERATOR, scalar);

function toAffine(point) {
  if (isInfinity(point)) throw new Error('the identity element has no affine form');
  const zInv = modInverse(point.z, P);
  const zInv2 = mod(zInv * zInv);
  return { x: mod(point.x * zInv2), y: mod(point.y * zInv2 * zInv) };
}

function isOnCurve(x, y) {
  return mod(y * y) === mod(mod(x * x * x) + mod(A * x) + B);
}

/* ------------------------------------------------------------------ *
 * Serialisation
 * ------------------------------------------------------------------ */

/** SerializeElement: SEC1 compressed form, 33 bytes. */
export function serializeElement(point) {
  const { x, y } = toAffine(point);
  return concat(i2osp1(y & 1n ? 0x03 : 0x02), bigIntToBytes(x, 32));
}

/**
 * DeserializeElement, with the validation an OPRF participant needs. A point
 * that is not on the curve is not merely malformed: multiplying one by a secret
 * key leaks that key a residue at a time.
 */
export function deserializeElement(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.length !== ELEMENT_BYTES) {
    throw new Error(`element must be ${ELEMENT_BYTES} bytes in SEC1 compressed form`);
  }
  const prefix = bytes[0];
  if (prefix !== 0x02 && prefix !== 0x03) {
    throw new Error('element must use a compressed SEC1 prefix (0x02 or 0x03)');
  }

  const x = bytesToBigInt(bytes.subarray(1));
  if (x >= P) throw new Error('element x-coordinate is out of range');

  const alpha = mod(mod(x * x * x) + mod(A * x) + B);
  let y = modPow(alpha, SQRT_EXP, P);
  if (mod(y * y) !== alpha) throw new Error('element is not a point on P-256');
  if ((y & 1n) !== BigInt(prefix & 1)) y = mod(P - y);

  if (!isOnCurve(x, y)) throw new Error('element is not a point on P-256');
  if (x === 0n && y === 0n) throw new Error('element must not be the identity');

  return { x, y, z: 1n };
}

export const serializeScalar = (scalar) => bigIntToBytes(mod(scalar, N), SCALAR_BYTES);

export function deserializeScalar(bytes) {
  if (!(bytes instanceof Uint8Array) || bytes.length !== SCALAR_BYTES) {
    throw new Error(`scalar must be ${SCALAR_BYTES} bytes`);
  }
  const scalar = bytesToBigInt(bytes);
  if (scalar >= N) throw new Error('scalar is not reduced modulo the group order');
  return scalar;
}

/**
 * A uniform scalar in [1, order - 1]. Zero is excluded: a zero blind would send
 * the identity to the server and remove the blinding entirely.
 */
export function randomScalar() {
  for (;;) {
    const candidate = bytesToBigInt(crypto.getRandomValues(new Uint8Array(SCALAR_BYTES)));
    if (candidate >= 1n && candidate < N) return candidate;
  }
}

/* ------------------------------------------------------------------ *
 * Ciphersuite context (RFC 9497 Section 3.1)
 * ------------------------------------------------------------------ */

export const CIPHERSUITE = 'P256-SHA256';
const MODE_VOPRF = 0x01;

const CONTEXT_STRING = concat(ASCII.encode('OPRFV1-'), i2osp1(MODE_VOPRF), ASCII.encode('-'), ASCII.encode(CIPHERSUITE));

const DST_HASH_TO_GROUP = concat(ASCII.encode('HashToGroup-'), CONTEXT_STRING);
const DST_HASH_TO_SCALAR = concat(ASCII.encode('HashToScalar-'), CONTEXT_STRING);
const DST_SEED = concat(ASCII.encode('Seed-'), CONTEXT_STRING);
const LABEL_COMPOSITE = ASCII.encode('Composite');
const LABEL_CHALLENGE = ASCII.encode('Challenge');
const LABEL_FINALIZE = ASCII.encode('Finalize');

/* ------------------------------------------------------------------ *
 * Hash to curve (RFC 9380)
 * ------------------------------------------------------------------ */

/** expand_message_xmd with SHA-256, RFC 9380 Section 5.3.1. */
async function expandMessageXmd(message, dst, lengthInBytes) {
  if (dst.length > 255) throw new Error('domain separation tag exceeds 255 bytes');
  const ell = Math.ceil(lengthInBytes / 32);
  if (ell > 255 || lengthInBytes > 65535) throw new Error('requested expansion length is out of range');

  const dstPrime = concat(dst, i2osp1(dst.length));
  const zPad = new Uint8Array(64);

  const b0 = await sha256(concat(zPad, message, i2osp2(lengthInBytes), i2osp1(0), dstPrime));

  const blocks = [await sha256(concat(b0, i2osp1(1), dstPrime))];
  for (let i = 2; i <= ell; i += 1) {
    const xored = new Uint8Array(32);
    for (let j = 0; j < 32; j += 1) xored[j] = b0[j] ^ blocks[i - 2][j];
    blocks.push(await sha256(concat(xored, i2osp1(i), dstPrime)));
  }

  return concat(...blocks).subarray(0, lengthInBytes);
}

/** hash_to_field for a prime field with m = 1. */
async function hashToField(message, dst, count) {
  const uniform = await expandMessageXmd(message, dst, count * FIELD_ELEMENT_BYTES);
  const elements = [];
  for (let i = 0; i < count; i += 1) {
    const chunk = uniform.subarray(i * FIELD_ELEMENT_BYTES, (i + 1) * FIELD_ELEMENT_BYTES);
    elements.push(mod(bytesToBigInt(chunk)));
  }
  return elements;
}

const curveEquation = (x) => mod(mod(x * x * x) + mod(A * x) + B);
const sgn0 = (value) => Number(value & 1n);

/** map_to_curve_simple_swu, RFC 9380 Section 6.6.2. */
function mapToCurveSimpleSwu(u) {
  const uSquared = mod(u * u);
  const zuSquared = mod(Z * uSquared);
  const tv1 = inv0(mod(mod(zuSquared * zuSquared) + zuSquared));

  const x1 = tv1 === 0n
    ? mod(B * inv0(mod(Z * A)))
    : mod(mod(mod(P - B) * inv0(A)) * mod(1n + tv1));

  const gx1 = curveEquation(x1);
  const x2 = mod(zuSquared * x1);

  let x;
  let y;
  const candidate = modPow(gx1, SQRT_EXP, P);
  if (mod(candidate * candidate) === gx1) {
    x = x1;
    y = candidate;
  } else {
    x = x2;
    y = modPow(curveEquation(x2), SQRT_EXP, P);
  }

  if (sgn0(u) !== sgn0(y)) y = mod(P - y);

  return { x, y, z: 1n };
}

/**
 * hash_to_curve. Two independent SSWU maps are added together because a single
 * map covers only about half the curve, which would be distinguishable from a
 * random oracle. Note also what this is not: try-and-increment, whose iteration
 * count depends on the input and would leak the identifier through timing.
 */
export async function hashToGroup(message) {
  const [u0, u1] = await hashToField(message, DST_HASH_TO_GROUP, 2);
  // P-256 has cofactor 1, so no cofactor clearing is needed.
  return add(mapToCurveSimpleSwu(u0), mapToCurveSimpleSwu(u1));
}

async function hashToScalar(input) {
  const uniform = await expandMessageXmd(input, DST_HASH_TO_SCALAR, 48);
  return mod(bytesToBigInt(uniform), N);
}

/* ------------------------------------------------------------------ *
 * DLEQ verification (RFC 9497 Section 2.2.2)
 * ------------------------------------------------------------------ */

export function deserializeProof(hex) {
  const bytes = hexToBytes(hex);
  if (bytes.length !== 2 * SCALAR_BYTES) throw new Error('proof must be 64 bytes');
  return {
    challenge: deserializeScalar(bytes.subarray(0, SCALAR_BYTES)),
    response: deserializeScalar(bytes.subarray(SCALAR_BYTES)),
  };
}

async function computeComposites(publicKey, blindedElements, evaluatedElements) {
  const seed = await sha256(concat(lp(serializeElement(publicKey)), lp(DST_SEED)));

  let m = INFINITY;
  let z = INFINITY;
  for (let i = 0; i < blindedElements.length; i += 1) {
    const transcript = concat(
      lp(seed),
      i2osp2(i),
      lp(serializeElement(blindedElements[i])),
      lp(serializeElement(evaluatedElements[i])),
      LABEL_COMPOSITE,
    );
    const di = await hashToScalar(transcript);
    m = add(scalarMultiply(blindedElements[i], di), m);
    z = add(scalarMultiply(evaluatedElements[i], di), z);
  }
  return { m, z };
}

async function challengeScalar(publicKey, m, z, t2, t3) {
  return hashToScalar(concat(
    lp(serializeElement(publicKey)),
    lp(serializeElement(m)),
    lp(serializeElement(z)),
    lp(serializeElement(t2)),
    lp(serializeElement(t3)),
    LABEL_CHALLENGE,
  ));
}

/**
 * Checks that the key behind the returned evaluations is the key behind the
 * published public key, learning nothing about that key in the process.
 *
 * Skipping this check is the interesting attack. A server that answered one
 * bank under a private key of its own would isolate that bank's pseudonyms from
 * every other bank's, while remaining able to link all of them itself. Every
 * participant would see a working system and share nothing.
 */
export async function verifyProof(publicKey, blindedElements, evaluatedElements, proof) {
  if (blindedElements.length === 0 || blindedElements.length !== evaluatedElements.length) {
    return false;
  }
  const { m, z } = await computeComposites(publicKey, blindedElements, evaluatedElements);

  const t2 = add(scalarMultiplyGenerator(proof.response), scalarMultiply(publicKey, proof.challenge));
  const t3 = add(scalarMultiply(m, proof.response), scalarMultiply(z, proof.challenge));
  if (isInfinity(t2) || isInfinity(t3)) return false;

  const expected = await challengeScalar(publicKey, m, z, t2, t3);
  return expected === proof.challenge;
}

/* ------------------------------------------------------------------ *
 * The protocol
 * ------------------------------------------------------------------ */

/**
 * Blind: map the identifier onto the curve, then multiply by a fresh random
 * scalar. The result is uniform in the group and independent of the input, so
 * what travels to the server carries no recoverable trace of the identifier.
 *
 * The blind must be fresh for every evaluation. Reusing one across two
 * identifiers would let the server tell whether they were equal simply by
 * comparing the bytes it received.
 */
export async function blind(input) {
  const inputElement = await hashToGroup(input);
  if (isInfinity(inputElement)) throw new Error('identifier maps to the identity element');

  const blindScalar = randomScalar();
  return { blind: blindScalar, blindedElement: scalarMultiply(inputElement, blindScalar) };
}

/**
 * Finalize: verify the proof, strip the blind with its modular inverse, and
 * hash the result into the pseudonym.
 *
 * The unblinding is the neat part. The server returned k·(r·P); multiplying by
 * r inverse leaves k·P, which depends only on the identifier and the server
 * key. Two banks that never speak to each other, using unrelated blinds, arrive
 * at the same value.
 */
export async function finalize(input, blindScalar, evaluatedElement, blindedElement, publicKey, proof) {
  const verified = await verifyProof(publicKey, [blindedElement], [evaluatedElement], proof);
  if (!verified) throw new Error('the server could not prove it used the published key');

  const unblinded = scalarMultiply(evaluatedElement, modInverse(blindScalar, N));

  return sha256(concat(
    lp(input),
    lp(serializeElement(unblinded)),
    LABEL_FINALIZE,
  ));
}

/* ------------------------------------------------------------------ *
 * Identifier canonicalisation
 * ------------------------------------------------------------------ */

export const IDENTIFIER_TYPES = {
  AZ_FIN: 'az-fin',
  AZ_TIN: 'az-tin',
  PASSPORT: 'passport',
};

/**
 * Must match teknofest.signa.producer.crypto.PiiNormalizer byte for byte.
 *
 * The OPRF is a function of exact bytes, so "7gk4m2q" and "7GK4M2Q" produce
 * unrelated pseudonyms. If the console and the server disagreed on so much as a
 * character class, the same customer would enrol twice and never match, with no
 * error anywhere to show for it. scripts/oprf.parity.mjs pins the fixtures that
 * both sides are checked against.
 */
export function canonicalize(identifierType, rawIdentifier) {
  if (!rawIdentifier || !rawIdentifier.trim()) {
    throw new Error('identifier must not be empty');
  }
  const normalized = rawIdentifier
    .normalize('NFKC')
    .replace(/[\p{White_Space}\p{Cf}-]/gu, '')
    .toUpperCase();

  if (!normalized) throw new Error('identifier contains no significant characters');

  return ASCII.encode(`${identifierType}:${normalized}`);
}

/**
 * The whole bank-side flow for one identifier.
 *
 * `evaluateBatch` is injected rather than imported so this function stays
 * transport agnostic, and so the test vectors can drive it without a network.
 */
export async function derivePseudonym({ identifierType, identifier, publicKeyHex, evaluateBatch }) {
  const input = canonicalize(identifierType, identifier);
  const publicKey = deserializeElement(hexToBytes(publicKeyHex));

  const { blind: blindScalar, blindedElement } = await blind(input);
  const blindedHex = bytesToHex(serializeElement(blindedElement));

  const response = await evaluateBatch([blindedHex]);

  const evaluatedElement = deserializeElement(hexToBytes(response.evaluatedElements[0]));
  const proof = deserializeProof(response.proof);

  const pseudonym = await finalize(input, blindScalar, evaluatedElement, blindedElement, publicKey, proof);

  return {
    pseudonym: bytesToHex(pseudonym),
    keyId: response.keyId,
    blindedElement: blindedHex,
    evaluatedElement: response.evaluatedElements[0],
    proof: response.proof,
  };
}

export const __testing = {
  P, N, GENERATOR, scalarMultiply, scalarMultiplyGenerator, add, toAffine,
  hashToScalar, expandMessageXmd, deserializeScalar, isInfinity,
};
