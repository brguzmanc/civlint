package com.bguzman.civlint.domain;

/**
 * Kinds of evidence recognised by the synthetic name-and-record correction procedure.
 *
 * <p>Every constant is invented for demonstration. None corresponds to a real document issued by any
 * real jurisdiction.
 *
 * <p>{@link #authoritative()} marks the sources whose disagreement constitutes a genuine conflict.
 * Two authoritative sources claiming different values for the same field is the signal that a case
 * cannot be decided mechanically, however complete the file looks.
 */
public enum EvidenceType {

    /** Extract of the original birth entry held by a regional office. */
    BIRTH_RECORD_EXTRACT("Birth record extract", true),

    /** The regional registry entry currently in force. */
    REGIONAL_REGISTRY_ENTRY("Regional registry entry", true),

    /** The national registry entry, where one has been created. */
    NATIONAL_REGISTRY_ENTRY("National registry entry", true),

    /** A certified order from a fictional civil court directing a name change. */
    COURT_NAME_CHANGE_ORDER("Certified name-change order", true),

    /** A photographic identity document. */
    IDENTITY_DOCUMENT("Identity document", false),

    /** A declaration signed by the applicant. */
    SWORN_DECLARATION("Sworn declaration", false),

    /** Correspondence offered in support of the request. */
    SUPPORTING_CORRESPONDENCE("Supporting correspondence", false),

    /** An attestation accepted in place of a document the applicant cannot obtain or produce. */
    ALTERNATIVE_ATTESTATION("Alternative attestation", false),

    /** A certified translation accompanying another item. */
    TRANSLATION_CERTIFICATE("Translation certificate", false);

    private final String label;
    private final boolean authoritative;

    EvidenceType(String label, boolean authoritative) {
        this.label = label;
        this.authoritative = authoritative;
    }

    public String label() {
        return label;
    }

    public boolean authoritative() {
        return authoritative;
    }
}
