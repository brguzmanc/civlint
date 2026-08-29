/**
 * Technical primitives shared by every other module: canonical JSON, SHA-256 hashing and
 * identifier validation.
 *
 * <p>This module is deliberately dependency-free (no Spring, no Jackson, no persistence). It sits
 * below the domain in the dependency order so that determinism guarantees cannot be broken by an
 * upgrade to a serialization library.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Support",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.bguzman.civlint.support;
