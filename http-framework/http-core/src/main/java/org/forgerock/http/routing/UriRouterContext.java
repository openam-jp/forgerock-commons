/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2012-2016 ForgeRock AS.
 */

package org.forgerock.http.routing;

import static java.lang.String.format;
import static org.forgerock.util.Reject.checkNotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;
import org.forgerock.util.Reject;

/**
 * A {@link Context} which is created when a request has been routed. The
 * context includes:
 * <ul>
 * <li>the portion of the request URI which matched the URI template
 * <li>the portion of the request URI which is remaining to be matched</li>
 * <li>a method for obtaining the base URI, which represents the portion of the
 * request URI which has been routed so far. This is obtained dynamically by
 * concatenating the matched URI with matched URIs in parent router contexts
 * <li>a map which contains the parsed URI template variables, keyed on the URI
 * template variable name.
 * </ul>
 */
public final class UriRouterContext extends AbstractContext {

    // Persisted attribute names
    private static final String ATTR_MATCHED_URI = "matchedUri";
    private static final String ATTR_REMAINIG_URI = "remainingUri";
    private static final String ATTR_URI_TEMPLATE_VARIABLES = "uriTemplateVariables";
    private static final String ATTR_ORIGINAL_URI = "originalUri";

    private final Map<String, String> uriTemplateVariables;

    /**
     * The original message's URI, as received by the web container. This value is set by the receiving servlet and
     * is immutable.
     */
    private URI originalUri;

        /**
     * Creates a new routing context having the provided parent, URI template
     * variables, and an ID automatically generated using
     * {@code UUID.randomUUID()}.
     *
     * @param parent
     *            The parent server context.
     * @param matchedUri
     *            The matched URI
     * @param remainingUri
     *            The remaining URI to be matched.
     * @param uriTemplateVariables
     *            A {@code Map} containing the parsed URI template variables,
     *            keyed on the URI template variable name.
     */
    public UriRouterContext(final Context parent, final String matchedUri, final String remainingUri,
            final Map<String, String> uriTemplateVariables) {
        this(parent, matchedUri, remainingUri, uriTemplateVariables, null);
    }

    /**
     * Creates a new routing context having the provided parent, URI template
     * variables, and an ID automatically generated using
     * {@code UUID.randomUUID()}.
     *
     * @param parent
     *            The parent server context.
     * @param matchedUri
     *            The matched URI
     * @param remainingUri
     *            The remaining URI to be matched.
     * @param uriTemplateVariables
     *            A {@code Map} containing the parsed URI template variables,
     *            keyed on the URI template variable name.
     * @param originalUri
     *            The original URI
     */
    public UriRouterContext(final Context parent, final String matchedUri, final String remainingUri,
            final Map<String, String> uriTemplateVariables, URI originalUri) {
        super(checkNotNull(parent, "Cannot instantiate UriRouterContext with null parent Context"), "router");
        data.put(ATTR_MATCHED_URI, matchedUri);
        data.put(ATTR_REMAINIG_URI, remainingUri);
        this.uriTemplateVariables = Collections.unmodifiableMap(uriTemplateVariables);
        data.put(ATTR_URI_TEMPLATE_VARIABLES, this.uriTemplateVariables);

        if (originalUri != null) {
            if (parent.containsContext(UriRouterContext.class)) {
                UriRouterContext parentUriRouterContext = parent.asContext(UriRouterContext.class);
                Reject.ifTrue(parentUriRouterContext.getOriginalUri() != null, "Cannot set the originalUri more than "
                        + "once in the chain.");
            }
            this.originalUri = originalUri;
            data.put(ATTR_ORIGINAL_URI, originalUri.toASCIIString());
        }
    }

    /**
     * Restore from JSON representation.
     *
     * @param savedContext
     *            The JSON representation from which this context's attributes
     *            should be parsed.
     * @param classLoader
     *            The ClassLoader which can properly resolve the persisted class-name.
     */
    public UriRouterContext(final JsonValue savedContext, final ClassLoader classLoader) {
        super(savedContext, classLoader);
        this.uriTemplateVariables =
                Collections.unmodifiableMap(data.get(ATTR_URI_TEMPLATE_VARIABLES).required().asMap(String.class));

        final String savedUri = data.get(ATTR_ORIGINAL_URI).asString();
        try {
            this.originalUri = new URI(savedUri);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(format("The URI %s is not valid", savedUri));
        }
    }

    /**
     * Returns the portion of the request URI which has been routed so far. This
     * is obtained dynamically by concatenating the matched URI with the base
     * URI of the parent router context if present. The base URI is never
     * {@code null} but may be "" (empty string).
     *
     * @return The non-{@code null} portion of the request URI which has been
     *         routed so far.
     */
    public String getBaseUri() {
        final StringBuilder builder = new StringBuilder();
        final Context parent = getParent();
        if (parent.containsContext(UriRouterContext.class)) {
            final String baseUri = parent.asContext(UriRouterContext.class).getBaseUri();
            if (!baseUri.isEmpty()) {
                builder.append(baseUri);
            }
        }
        final String matchedUri = getMatchedUri();
        if (matchedUri.length() > 0) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(matchedUri);
        }
        return builder.toString();
    }

    /**
     * Returns the portion of the request URI which matched the URI template.
     * The matched URI is never {@code null} but may be "" (empty string).
     *
     * @return The non-{@code null} portion of the request URI which matched the
     *         URI template.
     */
    public String getMatchedUri() {
        return data.get(ATTR_MATCHED_URI).asString();
    }

    /**
     * Returns the portion of the request URI which is remaining to be matched
     * be the next router. The remaining URI is never {@code null} but may be
     * "" (empty string).
     *
     * @return The non-{@code null} portion of the request URI which is
     * remaining to be matched.
     */
    public String getRemainingUri() {
        return data.get(ATTR_REMAINIG_URI).asString();
    }

    /**
     * Returns an unmodifiable {@code Map} containing the parsed URI template
     * variables, keyed on the URI template variable name.
     *
     * @return The unmodifiable {@code Map} containing the parsed URI template
     *         variables, keyed on the URI template variable name.
     */
    public Map<String, String> getUriTemplateVariables() {
        return uriTemplateVariables;
    }

    /**
     * Get the original URI.
     *
     * @return The original URI
     */
    public URI getOriginalUri() {
        final Context parent = getParent();

        if (this.originalUri != null) {
            return this.originalUri;
        } else if (parent.containsContext(UriRouterContext.class)) {
            return parent.asContext(UriRouterContext.class).getOriginalUri();
        } else {
            return null;
        }
    }

    /**
     * Return a builder for a new {@link UriRouterContext}.
     * @param parent parent context
     * @return a builder for a new {@link UriRouterContext}.
     */
    public static Builder uriRouterContext(Context parent) {
        return new Builder(parent);
    }

    /**
     * Ease {@link UriRouterContext} construction.
     */
    public static class Builder {

        private final Context parent;
        private String matchedUri;
        private String remainingUri;
        private URI originalUri;
        private Map<String, String> variables = new LinkedHashMap<>();

        Builder(final Context parent) {
            this.parent = parent;
        }

        /**
         * Set the {@code matchedUri} value.
         * @param matchedUri matched uri
         * @return this builder
         */
        public Builder matchedUri(String matchedUri) {
            this.matchedUri = matchedUri;
            return this;
        }

        /**
         * Set the {@code remainingUri} value.
         * @param remainingUri remaining uri
         * @return this builder
         */
        public Builder remainingUri(String remainingUri) {
            this.remainingUri = remainingUri;
            return this;
        }

        /**
         * Set the {@code originalUri} value (only first UriRouterContext is expected to have that value set).
         * @param originalUri original uri
         * @return this builder
         */
        public Builder originalUri(URI originalUri) {
            this.originalUri = originalUri;
            return this;
        }

        /**
         * Set the {@code variables} value.
         * @param variables matched variables
         * @return this builder
         */
        public Builder templateVariables(Map<String, String> variables) {
            this.variables = checkNotNull(variables);
            return this;
        }

        /**
         * Add the given {@code name}:{@code value} pair in the {@code variables} map.
         * @param name matched variable name
         * @param value matched variable value
         * @return this builder
         */
        public Builder templateVariable(String name, String value) {
            this.variables.put(name, value);
            return this;
        }

        /**
         * Returns a new {@link UriRouterContext} build from provided values.
         * @return a new {@link UriRouterContext}.
         */
        public UriRouterContext build() {
            return new UriRouterContext(parent, matchedUri, remainingUri, variables, originalUri);
        }
    }
}
