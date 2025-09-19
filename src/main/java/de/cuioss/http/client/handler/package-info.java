/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
 * Provides HTTP-related utilities and helpers, such as status family detection and secure SSL context providers.
 *
 * <p>This package provides HTTP client handling utilities including:
 * <ul>
 *   <li>{@link de.cuioss.http.client.handler.HttpHandler} - Builder-based HTTP client configuration</li>
 *   <li>{@link de.cuioss.http.client.handler.HttpStatusFamily} - HTTP status code categorization</li>
 *   <li>{@link de.cuioss.http.client.handler.SecureSSLContextProvider} - Secure SSL/TLS context management</li>
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
 */
@NullMarked
package de.cuioss.http.client.handler;

import org.jspecify.annotations.NullMarked;
