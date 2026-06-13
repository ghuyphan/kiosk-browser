# Release v1.7.2: Drop-shadow Stroked Cursor & Adaptive Rate Limiting

This release brings design refinement to the Remote Control cursor representation and adds client-side throttling to ensure high responsiveness without rate-limiting penalties.

### Key Changes

* **Drop-Shadow Stroked Cursor**:
  * Refactored the cursor layout to use a single-path stroke model, resolving the subpixel alignment issues, asymmetric thickness, and skewing present in the previous iteration.
  * Added a semi-transparent black drop-shadow layer offset by `(+1dp, +1.5dp)` for a crisp, native OS-like cursor appearance.
* **Adaptive Rate Limiting**:
  * Dropped the base pointer tracking transmission interval to a hyper-responsive `150ms` (down from `500ms`).
  * Implemented an active timestamp logger tracking outbound requests in a rolling 5-second window.
  * Automatically increases transmission delay (to `300ms` or `600ms`) during continuous movement to avoid `ntfy.sh` `429 Too Many Requests` rate limiting, dropping back to `150ms` immediately upon a brief pause.
