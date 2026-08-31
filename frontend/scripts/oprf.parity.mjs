/**
 * Canonicalisation parity with the Java server.
 *
 * The fixtures below are the same ones pinned in
 * producer-main/src/test/java/teknofest/signa/producer/crypto/PiiNormalizerTest.java.
 * Both sides assert the same expected bytes rather than comparing against each
 * other, so neither can quietly drift.
 *
 * Why this matters more than it looks: the OPRF is a function of exact bytes.
 * If the console stripped a non-breaking space and the server did not, the same
 * customer would enrol under two unrelated pseudonyms. Nothing would throw. The
 * network would simply stop linking that person, and the only symptom would be
 * fraud getting through.
 */

import { canonicalize, bytesToHex, IDENTIFIER_TYPES } from '../src/lib/oprf.js';

let passed = 0;
let failed = 0;

function check(label, expected, actual) {
  if (expected === actual) {
    passed += 1;
    console.log(`  pass  ${label}`);
  } else {
    failed += 1;
    console.log(`  FAIL  ${label}\n        expected ${expected}\n        actual   ${actual}`);
  }
}

/** UTF-8 of "az-fin:7GK4M2Q". */
const CANONICAL_FIN = '617a2d66696e3a37474b344d3251';

const EQUIVALENT_INPUTS = [
  ['plain', '7GK4M2Q'],
  ['lower case', '7gk4m2q'],
  ['surrounding spaces', '  7GK4M2Q  '],
  ['hyphenated', '7GK-4M2Q'],
  ['inner space', '7gk 4m2q'],
  ['non-breaking space', '7GK 4M2Q'],
  ['zero-width joiner', '7GK‍4M2Q'],
  ['left-to-right mark', '‎7GK4M2Q'],
];

console.log('Canonicalisation parity with the Java server\n');

for (const [label, raw] of EQUIVALENT_INPUTS) {
  check(`  ${label}`, CANONICAL_FIN, bytesToHex(canonicalize(IDENTIFIER_TYPES.AZ_FIN, raw)));
}

check(
  '  full-width digits fold through NFKC',
  bytesToHex(canonicalize(IDENTIFIER_TYPES.AZ_FIN, '1234567')),
  bytesToHex(canonicalize(IDENTIFIER_TYPES.AZ_FIN, '１２３４５６７')),
);

const fin = bytesToHex(canonicalize(IDENTIFIER_TYPES.AZ_FIN, '1234567'));
const tin = bytesToHex(canonicalize(IDENTIFIER_TYPES.AZ_TIN, '1234567'));
if (fin !== tin) {
  passed += 1;
  console.log('  pass    identifier types stay in separate pseudonym spaces');
} else {
  failed += 1;
  console.log('  FAIL    identifier types collide');
}

try {
  canonicalize(IDENTIFIER_TYPES.AZ_FIN, '---');
  failed += 1;
  console.log('  FAIL    an identifier with nothing significant left was accepted');
} catch {
  passed += 1;
  console.log('  pass    an identifier with nothing significant left is refused');
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed > 0 ? 1 : 0);
