package com.bguzman.civlint.domain;

import com.bguzman.civlint.support.CanonicalJson;
import com.bguzman.civlint.support.Identifiers;
import com.bguzman.civlint.support.Json;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * An approved set of policy rules, versioned and hashable.
 *
 * <p>The pack hash is the anchor of every reproducibility claim CivLint makes. A run records the
 * hash of the pack it used, so a later run that reports a different verdict can be distinguished
 * from a bug: either the hash differs, in which case the policy changed, or it matches, in which
 * case something non-deterministic has crept in.
 *
 * <p><strong>Invariants:</strong> rule identifiers are unique; rules are stored in ascending
 * identifier order; at least one rule is present.
 *
 * @param packId stable identifier
 * @param version version string
 * @param title human-readable title
 * @param jurisdictionNote statement of which fictional jurisdiction this describes
 * @param rules the rules, in ascending identifier order
 */
public record PolicyPack(
        String packId, String version, String title, String jurisdictionNote, List<PolicyRule> rules) {

    public PolicyPack {
        packId = Identifiers.requireStable("packId", packId);
        version = Identifiers.requireText("version", version);
        title = Identifiers.requireText("title", title);
        jurisdictionNote = Identifiers.requireText("jurisdictionNote", jurisdictionNote);
        Objects.requireNonNull(rules, "rules");
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("A policy pack must contain at least one rule");
        }
        TreeSet<String> seen = new TreeSet<>();
        for (PolicyRule rule : rules) {
            Objects.requireNonNull(rule, "rule");
            if (!seen.add(rule.ruleId())) {
                throw new IllegalArgumentException("Duplicate rule identifier " + rule.ruleId());
            }
        }
        rules = rules.stream().sorted(Comparator.comparing(PolicyRule::ruleId)).toList();
    }

    public Optional<PolicyRule> rule(String ruleId) {
        return rules.stream().filter(r -> r.ruleId().equals(ruleId)).findFirst();
    }

    public Json toJson() {
        return Json.obj()
                .put("packId", packId)
                .put("version", version)
                .put("title", title)
                .put("jurisdictionNote", jurisdictionNote)
                .put("rules", Json.array(rules.stream().map(PolicyRule::toJson).toList()))
                .build();
    }

    public String canonicalHash() {
        return CanonicalJson.hash(toJson());
    }
}
