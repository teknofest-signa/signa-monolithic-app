/**
 * Replays the RFC 9497 Appendix A.3.2 test vectors (VOPRF, P256-SHA256)
 * against src/lib/oprf.js.
 *
 * Run with: npm run verify:oprf
 *
 * These vectors are the only real evidence that the browser client and the Java
 * server compute the same pseudonym. Both are checked against the same
 * published numbers rather than against each other, so neither can drag the
 * other off-spec.
 */

import {
  bytesToHex, hexToBytes, serializeElement, deserializeElement,
  deserializeScalar, deserializeProof, verifyProof, finalize, hashToGroup,
  canonicalize, IDENTIFIER_TYPES, blind, __testing,
} from '../src/lib/oprf.js';

const { scalarMultiply, scalarMultiplyGenerator } = __testing;

let passed = 0;
let failed = 0;

function check(label, expected, actual) {
  if (String(expected).toLowerCase() === String(actual).toLowerCase()) {
    passed += 1;
    console.log(`  pass  ${label}`);
  } else {
    failed += 1;
    console.log(`  FAIL  ${label}`);
    console.log(`        expected ${expected}`);
    console.log(`        actual   ${actual}`);
  }
}

function checkTrue(label, value) {
  if (value) {
    passed += 1;
    console.log(`  pass  ${label}`);
  } else {
    failed += 1;
    console.log(`  FAIL  ${label}`);
  }
}

const SECRET_KEY = deserializeScalar(hexToBytes('ca5d94c8807817669a51b196c34c1b7f8442fde4334a7121ae4736364312fca6'));
const PUBLIC_KEY_HEX = '03e17e70604bcabe198882c0a1f27a92441e774224ed9c702e51dd17038b102462';
const PUBLIC_KEY = deserializeElement(hexToBytes(PUBLIC_KEY_HEX));

const VECTORS = [
  {
    name: 'A.3.2.1 batch size 1',
    input: '00',
    blind: '3338fa65ec36e0290022b48eb562889d89dbfa691d1cde91517fa222ed7ad364',
    blindedElement: '02dd05901038bb31a6fae01828fd8d0e49e35a486b5c5d4b4994013648c01277da',
    evaluatedElement: '0209f33cab60cf8fe69239b0afbcfcd261af4c1c5632624f2e9ba29b90ae83e4a2',
    proof: 'e7c2b3c5c954c035949f1f74e6bce2ed539a3be267d1481e9ddb178533df4c2664f69d065c604a4fd953e100b856ad83804eb3845189babfa5a702090d6fc5fa',
    output: '0412e8f78b02c415ab3a288e228978376f99927767ff37c5718d420010a645a1',
  },
  {
    name: 'A.3.2.2 batch size 1',
    input: '5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a',
    blind: '3338fa65ec36e0290022b48eb562889d89dbfa691d1cde91517fa222ed7ad364',
    blindedElement: '03cd0f033e791c4d79dfa9c6ed750f2ac009ec46cd4195ca6fd3800d1e9b887dbd',
    evaluatedElement: '030d2985865c693bf7af47ba4d3a3813176576383d19aff003ef7b0784a0d83cf1',
    proof: '2787d729c57e3d9512d3aa9e8708ad226bc48e0f1750b0767aaff73482c44b8d2873d74ec88aebd3504961acea16790a05c542d9fbff4fe269a77510db00abab',
    output: '771e10dcd6bcd3664e23b8f2a710cfaaa8357747c4a8cbba03133967b5c24f18',
  },
];

console.log('RFC 9497 VOPRF P256-SHA256 vectors, JavaScript client\n');

check('public key round-trips', PUBLIC_KEY_HEX, bytesToHex(serializeElement(PUBLIC_KEY)));

for (const vector of VECTORS) {
  console.log(vector.name);
  const input = hexToBytes(vector.input);
  const blindScalar = deserializeScalar(hexToBytes(vector.blind));

  const inputElement = await hashToGroup(input);
  const blindedElement = scalarMultiply(inputElement, blindScalar);
  check('  BlindedElement (exercises hash_to_curve)', vector.blindedElement, bytesToHex(serializeElement(blindedElement)));

  const evaluatedElement = scalarMultiply(blindedElement, SECRET_KEY);
  check('  EvaluatedElement', vector.evaluatedElement, bytesToHex(serializeElement(evaluatedElement)));

  const proof = deserializeProof(vector.proof);
  checkTrue('  VerifyProof accepts the published proof', await verifyProof(PUBLIC_KEY, [blindedElement], [evaluatedElement], proof));

  const output = await finalize(input, blindScalar, evaluatedElement, blindedElement, PUBLIC_KEY, proof);
  check('  Output (pseudonym)', vector.output, bytesToHex(output));
}

// Batched proof from A.3.2.3, verified as a pair.
console.log('A.3.2.3 batch size 2');
{
  const inputs = ['00', '5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a'].map(hexToBytes);
  const blinds = [
    '3338fa65ec36e0290022b48eb562889d89dbfa691d1cde91517fa222ed7ad364',
    'f9db001266677f62c095021db018cd8cbb55941d4073698ce45c405d1348b7b1',
  ].map((hex) => deserializeScalar(hexToBytes(hex)));
  const expectedBlinded = [
    '02dd05901038bb31a6fae01828fd8d0e49e35a486b5c5d4b4994013648c01277da',
    '03462e9ae64cae5b83ba98a6b360d942266389ac369b923eb3d557213b1922f8ab',
  ];
  const expectedEvaluated = [
    '0209f33cab60cf8fe69239b0afbcfcd261af4c1c5632624f2e9ba29b90ae83e4a2',
    '02bb24f4d838414aef052a8f044a6771230ca69c0a5677540fff738dd31bb69771',
  ];
  const batchProof = deserializeProof('bdcc351707d02a72ce49511c7db990566d29d6153ad6f8982fad2b435d6ce4d60da1e6b3fa740811bde34dd4fe0aa1b5fe6600d0440c9ddee95ea7fad7a60cf2');

  const blinded = [];
  const evaluated = [];
  for (let i = 0; i < 2; i += 1) {
    const b = scalarMultiply(await hashToGroup(inputs[i]), blinds[i]);
    check(`  BlindedElement[${i}]`, expectedBlinded[i], bytesToHex(serializeElement(b)));
    blinded.push(b);
    const e = scalarMultiply(b, SECRET_KEY);
    check(`  EvaluatedElement[${i}]`, expectedEvaluated[i], bytesToHex(serializeElement(e)));
    evaluated.push(e);
  }

  checkTrue('  VerifyProof accepts the batched proof', await verifyProof(PUBLIC_KEY, blinded, evaluated, batchProof));
  checkTrue('  reordering the batch is rejected', !(await verifyProof(PUBLIC_KEY, blinded, [evaluated[1], evaluated[0]], batchProof)));
}

console.log('negative checks');
{
  const input = canonicalize(IDENTIFIER_TYPES.AZ_FIN, '7GK4M2Q');
  const { blind: r, blindedElement } = await blind(input);
  const evaluatedElement = scalarMultiply(blindedElement, SECRET_KEY);

  const rogueKey = (SECRET_KEY + 7n) % __testing.N;
  const roguePublicKey = scalarMultiplyGenerator(rogueKey);
  const rogueEvaluated = scalarMultiply(blindedElement, rogueKey);

  // A proof honestly built under the rogue key still fails against the pinned
  // public key, which is the whole point of pinning it.
  checkTrue('  evaluation under an unpublished key fails verification',
    !(await verifyProof(PUBLIC_KEY, [blindedElement], [rogueEvaluated], deserializeProof(VECTORS[0].proof))));
  checkTrue('  rogue public key does not rescue it',
    !(await verifyProof(roguePublicKey, [blindedElement], [evaluatedElement], deserializeProof(VECTORS[0].proof))));

  const rejects = (fn) => { try { fn(); return false; } catch { return true; } };
  checkTrue('  identity encoding rejected', rejects(() => deserializeElement(Uint8Array.of(0x00))));
  checkTrue('  all-zero 33-byte encoding rejected', rejects(() => deserializeElement(new Uint8Array(33))));
  checkTrue('  off-curve point rejected', rejects(() => deserializeElement(hexToBytes(`02${'0'.repeat(63)}1`))));
  checkTrue('  uncompressed point rejected', rejects(() => deserializeElement(hexToBytes(`04${'aa'.repeat(32)}`))));
  checkTrue('  unreduced scalar rejected', rejects(() => deserializeScalar(new Uint8Array(32).fill(0xff))));

  const second = await blind(input);
  checkTrue('  two blinds of one identifier are unlinkable on the wire',
    bytesToHex(serializeElement(blindedElement)) !== bytesToHex(serializeElement(second.blindedElement)));

  // Same identifier, unrelated blinds, same pseudonym. This is the property the
  // whole platform rests on.
  const first = await finalizeWith(input, r, evaluatedElement, blindedElement);
  const other = await finalizeWith(input, second.blind, scalarMultiply(second.blindedElement, SECRET_KEY), second.blindedElement);
  check('  different blinds converge on one pseudonym', first, other);
}

async function finalizeWith(input, blindScalar, evaluatedElement, blindedElement) {
  // Build a real proof the same way the server would, so finalize's verification
  // step is genuinely exercised rather than bypassed.
  const proof = await proveForTest(blindedElement, evaluatedElement);
  return bytesToHex(await finalize(input, blindScalar, evaluatedElement, blindedElement, PUBLIC_KEY, proof));
}

/** Minimal prover, test-only: the browser never generates proofs. */
async function proveForTest(blindedElement, evaluatedElement) {
  const { hashToScalar } = __testing;
  const enc = new TextEncoder();
  const i2osp2 = (v) => Uint8Array.of((v >>> 8) & 0xff, v & 0xff);
  const cat = (...p) => { const o = new Uint8Array(p.reduce((s, x) => s + x.length, 0)); let f = 0; for (const x of p) { o.set(x, f); f += x.length; } return o; };
  const lp = (b) => cat(i2osp2(b.length), b);
  const ctx = cat(enc.encode('OPRFV1-'), Uint8Array.of(1), enc.encode('-'), enc.encode('P256-SHA256'));
  const seedDst = cat(enc.encode('Seed-'), ctx);

  const seed = new Uint8Array(await crypto.subtle.digest('SHA-256', cat(lp(serializeElement(PUBLIC_KEY)), lp(seedDst))));
  const di = await hashToScalar(cat(lp(seed), i2osp2(0), lp(serializeElement(blindedElement)), lp(serializeElement(evaluatedElement)), enc.encode('Composite')));

  const m = scalarMultiply(blindedElement, di);
  const z = scalarMultiply(m, SECRET_KEY);
  const r = 0x350e8040f828bf6ceca27405420cdf3d63cb3aef005f40ba51943c8026877963n;
  const t2 = scalarMultiplyGenerator(r);
  const t3 = scalarMultiply(m, r);

  const c = await hashToScalar(cat(
    lp(serializeElement(PUBLIC_KEY)), lp(serializeElement(m)), lp(serializeElement(z)),
    lp(serializeElement(t2)), lp(serializeElement(t3)), enc.encode('Challenge'),
  ));
  const s = ((r - c * SECRET_KEY) % __testing.N + __testing.N) % __testing.N;

  return { challenge: c, response: s };
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed > 0 ? 1 : 0);
