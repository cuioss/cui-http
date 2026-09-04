/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Configuration management for HTTP security validation.
 *
 * <p>This package provides immutable configuration objects and builders for customizing
 * the behavior of HTTP security validation. All configuration follows secure-by-default
 * principles with OWASP and RFC-based default values.</p>
 *
 * <h3>Configuration Components</h3>
 * <ul>
 *   <li>{@link de.cuioss.http.security.config.SecurityConfiguration} - Main immutable configuration record</li>
 *   <li>{@link de.cuioss.http.security.config.SecurityConfigurationBuilder} - Builder for configuration creation</li>
 *   <li>{@link de.cuioss.http.security.config.SecurityDefaults} - Default values and constants</li>
 * </ul>
 *
 * <h3>Configuration Categories</h3>
 * <ul>
 *   <li><strong>Length Limits</strong> - Maximum input lengths for different component types</li>
 *   <li><strong>Character Sets</strong> - Allowed characters for validation types</li>
 *   <li><strong>Feature Toggles</strong> - Enable/disable specific validation features</li>
 *   <li><strong>Pattern Configuration</strong> - Custom attack pattern definitions</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre><code>
 * // Use secure defaults
 * SecurityConfiguration defaultConfig = SecurityConfiguration.defaults();
 *
 * // Custom configuration
 * SecurityConfiguration customConfig = SecurityConfiguration.builder()
 *     .maxPathLength(2048)
 *     .maxParameterValueLength(8192)
 *     .allowDoubleEncoding(false)
 *     .normalizeUnicode(true)
 *     .failOnSuspiciousPatterns(true)
 *     .build();
 *
 * // Configuration is immutable after creation
 * assert customConfig.maxPathLength() == 2048;
 * </code></pre>
 *
 * <h3>Secure Defaults</h3>
 * <p>{@link de.cuioss.http.security.config.SecurityConfiguration#defaults()} is a balanced
 * baseline rather than a maximum-strictness profile. What it actually enables:</p>
 * <ul>
 *   <li>Conservative length and count limits to prevent DoS attacks (path 4096, parameter
 *       name/value 128/2048, header name/value 128/2048, cookie name/value 128/2048, body 5 MB)</li>
 *   <li>Null bytes, control characters and double encoding are rejected</li>
 *   <li>Unicode normalization is on, and comparisons are case-insensitive - which broadens,
 *       not narrows, attack-pattern matching</li>
 * </ul>
 * <p>What it deliberately leaves off, so it must be opted into when required:</p>
 * <ul>
 *   <li>{@code failOnSuspiciousPatterns} is {@code false} - a value starting with a protocol
 *       handler scheme such as {@code javascript:} is allowed through silently</li>
 *   <li>{@code allowExtendedAscii} is {@code true} - characters 128-255 are permitted</li>
 *   <li>{@code requireSecureCookies} and {@code requireHttpOnlyCookies} are {@code false}</li>
 *   <li>The header-name and content-type allow/block lists are empty, which means
 *       allow-all / block-none - and so are {@code blockedPathPatterns} and
 *       {@code blockedParameterNames}, the two application-layer content block-lists that
 *       {@link de.cuioss.http.security.config.SecurityConfiguration#paranoid()} seeds</li>
 * </ul>
 *
 * <h3>Package Nullability</h3>
 * <p>This package follows strict nullability conventions using JSpecify annotations:</p>
 * <ul>
 *   <li>All parameters and return values are non-null by default</li>
 *   <li>Nullable parameters and return values are explicitly annotated with {@code @Nullable}</li>
 * </ul>
 *
 * @since 1.0
 * @see de.cuioss.http.security.pipeline.PipelineFactory
 * @see de.cuioss.http.security.validation
 */
@NullMarked
package de.cuioss.http.security.config;

import org.jspecify.annotations.NullMarked;