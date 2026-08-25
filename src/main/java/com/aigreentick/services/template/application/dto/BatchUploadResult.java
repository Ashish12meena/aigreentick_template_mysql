package com.aigreentick.services.template.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Locale;

/**
 * Mirror of storage-service's {@code BatchUploadResponse}.
 *
 * <p><b>This class is a wire contract, not a domain type.</b> Its shape is
 * dictated entirely by what storage-service serialises; do not "tidy" a field
 * into a more convenient type. The previous version declared {@code error} as a
 * {@code String} while storage-service sends an object, and Jackson's failure to
 * bind ONE failed entry discarded the whole response — including every
 * successful upload in the same batch. The files were stored; the caller simply
 * could not read the reply, and every batch surfaced as a null result.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} at every level is
 * deliberate for the same reason. Storage-service adding a field must not be
 * able to break media sync: strict binding turns an additive, backward-
 * compatible change on their side into an outage on ours.
 *
 * <p>{@code results} is in REQUEST ORDER with exactly one entry per submitted
 * file, and entries are matched to tasks BY POSITION.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchUploadResult {

    private int successCount;
    private int failedCount;
    private List<BatchFileResult> results;

    /**
     * One submitted file's outcome, flat.
     *
     * <p>Storage-service deliberately does NOT suppress nulls here: every entry
     * carries all seven keys, so {@code error} is present and null on a success
     * and {@code url} is present and null on a failure. Read the fixed shape
     * rather than testing for key presence.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchFileResult {

        /** The filename sent in the multipart part, verbatim. */
        private String originalFilename;

        private Status status;

        /** Non-null only when {@code status} is {@link Status#SUCCESS}. */
        private String url;

        private String mediaType;
        private String contentType;
        private Long fileSizeBytes;

        /**
         * Null on success. An OBJECT on failure — this was the field typed as
         * {@code String}, and getting it wrong cost the entire response.
         */
        private BatchFileError error;

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        /** Stable error code, or null. Safe to call on a success. */
        public String errorCode() {
            return error == null ? null : error.getCode();
        }

        /** Client-safe message, or null. Safe to call on a success. */
        public String errorMessage() {
            return error == null ? null : error.getMessage();
        }

        /**
         * Storage-service sends three values. {@link #UNKNOWN} is ours.
         *
         * <p>The {@code @JsonCreator} exists so a value we have never heard of
         * deserialises to {@code UNKNOWN} rather than throwing. Without it, one
         * new status name added upstream would fail the whole response and take
         * every successful upload in the batch with it — the exact failure mode
         * the {@code error}-as-String bug produced. An enum is stricter than a
         * String, so it needs this escape hatch to be safe on a wire type.
         */
        public enum Status {
            SUCCESS,
            /** Attempted and rejected. Do NOT retry without changing the file. */
            FAILED,
            /**
             * Not attempted: storage-service's circuit breaker tripped earlier in
             * the batch. Safe to retry immediately — this is not bad content.
             */
            SKIPPED,
            /** Unrecognised value from upstream. Treated as a failure. */
            UNKNOWN;

            @JsonCreator
            public static Status from(String raw) {
                if (raw == null || raw.isBlank()) {
                    return UNKNOWN;
                }
                try {
                    return valueOf(raw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return UNKNOWN;
                }
            }
        }
    }

    /** @see BatchFileResult#error */
    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchFileError {

        /**
         * A stable storage-service {@code ErrorCode} name — branch on this, never
         * on {@code message}. The enum upstream is append-only and never renamed.
         * Values seen in practice: {@code QUOTA_NOT_PROVISIONED},
         * {@code QUOTA_EXCEEDED}, {@code CONTENT_TYPE_NOT_ALLOWED},
         * {@code CONTENT_TYPE_MISMATCH}, {@code MEDIA_TOO_LARGE},
         * {@code MEDIA_INVALID}, {@code STORAGE_UNAVAILABLE},
         * {@code BATCH_ITEM_SKIPPED}, {@code INTERNAL_ERROR}.
         */
        private String code;

        /**
         * Human-readable and client-safe by contract: never an internal exception
         * message, storage key, path, or provider error. Safe to log.
         */
        private String message;
    }
}