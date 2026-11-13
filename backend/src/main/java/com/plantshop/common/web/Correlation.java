package com.plantshop.common.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.MDC;

/**
 * Correlation identifiers shared by logs, responses and Problem Details (NFR-OBS-02). Trace and
 * span ids are put into the MDC by Micrometer Tracing; the request id and owner hash by our
 * filters.
 */
public final class Correlation {

  /** Response (and optional request) header carrying the request id. */
  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  /** MDC key of the request id. */
  public static final String MDC_REQUEST_ID = "requestId";

  /** MDC key of the authenticated owner's hash. */
  public static final String MDC_OWNER = "ownerHash";

  /** MDC key Micrometer Tracing uses for the trace id. */
  public static final String MDC_TRACE_ID = "traceId";

  private static final int OWNER_HASH_LENGTH = 8;

  private Correlation() {}

  /**
   * Returns the trace id of the current request, if tracing is active.
   *
   * @return the trace id
   */
  public static Optional<String> currentTraceId() {
    return Optional.ofNullable(MDC.get(MDC_TRACE_ID)).filter(id -> !id.isBlank());
  }

  /**
   * Pseudonymises an owner id for logs: the first 8 hex characters of its SHA-256 digest. Enough to
   * correlate one user's requests without logging the id itself.
   *
   * @param ownerId the owner id
   * @return the hash prefix
   */
  public static String ownerHash(String ownerId) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(ownerId.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, OWNER_HASH_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every Java platform", e);
    }
  }
}
