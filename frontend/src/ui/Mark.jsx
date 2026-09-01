/**
 * The Signa mark: two institutions, and only the overlap between them solid.
 * That is the whole product in a glyph — banks learn where their customers
 * coincide without either side handing over the names behind them.
 */
export default function Mark({ size = 16 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="5.6" cy="8" r="4.2" stroke="currentColor" strokeWidth="1.3" />
      <circle cx="10.4" cy="8" r="4.2" stroke="currentColor" strokeWidth="1.3" />
      <path
        d="M8 4.554A4.2 4.2 0 0 1 8 11.446A4.2 4.2 0 0 1 8 4.554Z"
        fill="currentColor"
      />
    </svg>
  );
}
