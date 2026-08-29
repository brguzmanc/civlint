/**
 * The REST API and the server-rendered dashboard.
 *
 * <p>Spring MVC plus Thymeleaf, with no client-side framework: the dashboard's job is to make evidence
 * readable, which does not require one.
 *
 * @author Buddy Guzman (bguzman)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Web",
        allowedDependencies = {"application", "domain", "procedure", "evaluation", "support"})
package com.bguzman.civlint.web;
