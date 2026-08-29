package com.bguzman.civlint.domain;

/**
 * Declared characteristics of a correction request that affect how it must be handled.
 *
 * <p>These flags describe the <em>request</em>, not the person. In particular there is deliberately
 * no flag meaning "unusual name": where a name cannot be compared mechanically, the limitation
 * belongs to CivLint's normaliser and is reported as {@link NameComparison.Undecidable}, not
 * recorded as a property of the applicant. See {@code docs/architecture.md}.
 */
public enum RequestFlag {

    /** The applicant asked for an accessibility accommodation. */
    ACCESSIBILITY_ACCOMMODATION_REQUESTED,

    /** The applicant offered alternative evidence in place of a required document. */
    ALTERNATIVE_EVIDENCE_OFFERED,

    /** The request is an appeal against an earlier decision. */
    APPEAL_REQUESTED,

    /** A third party has contested the record or the request. */
    CONTESTED_BY_THIRD_PARTY,

    /** The acting office claims authority delegated from another body. */
    DELEGATED_AUTHORITY_CLAIMED,

    /** The request pattern is not covered by any case type the policy pack recognises. */
    UNRECOGNISED_CASE_TYPE,

    /** Granting or refusing the request would have an adverse effect on the applicant's rights. */
    ADVERSE_OUTCOME_POSSIBLE,

    /** The submission duplicates an earlier one. */
    DUPLICATE_SUBMISSION
}
