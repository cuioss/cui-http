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
module de.cuioss.http {

    requires static lombok;
    requires org.jspecify;
    requires de.cuioss.java.tools;
    requires java.net.http;

    // Client HTTP utilities
    exports de.cuioss.http.client;
    exports de.cuioss.http.client.adapter;
    exports de.cuioss.http.client.converter;
    exports de.cuioss.http.client.handler;
    exports de.cuioss.http.client.result;
    exports de.cuioss.http.client.retry;

    // Security validation core
    exports de.cuioss.http.security.core;
    exports de.cuioss.http.security.config;
    exports de.cuioss.http.security.pipeline;
    exports de.cuioss.http.security.validation;
    exports de.cuioss.http.security.exceptions;

    // Security data models
    exports de.cuioss.http.security.data;

    // Security monitoring
    exports de.cuioss.http.security.monitoring;
}