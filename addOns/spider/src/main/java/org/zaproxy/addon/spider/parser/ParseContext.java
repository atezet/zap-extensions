/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2022 The ZAP Development Team
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
package org.zaproxy.addon.spider.parser;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.htmlparser.jericho.Source;
import org.apache.commons.httpclient.URI;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.ValueProvider;
import org.zaproxy.addon.spider.SpiderParam;
import org.zaproxy.zap.model.Context;
import org.zaproxy.zap.model.ValueGenerator;
import org.zaproxy.zap.users.User;

/**
 * A parse context.
 *
 * <p>Provides the data needed for parsing a HTTP message.
 */
public class ParseContext {

    private final SpiderParam spiderParam;
    private final ValueProvider valueProvider;
    private final HttpMessage httpMessage;
    private final String path;
    private final Context context;
    private final User user;
    private final int depth;
    private String baseUrl;
    private Source source;

    /**
     * Constructs a {@code ParseContext} with the given values.
     *
     * @param spiderParam the spider options, must not be {@code null}.
     * @param valueGenerator the value generator, must not be {@code null}.
     * @param httpMessage the message, must not be {@code null}.
     * @param path the path of the HTTP message.
     * @param depth the current depth of the parsing.
     * @throws NullPointerException if any of {@code spiderParam}, {@code valueGenerator}, or {@code
     *     httpMessage} is {@code null}.
     */
    public ParseContext(
            SpiderParam spiderParam,
            ValueProvider valueGenerator,
            HttpMessage httpMessage,
            String path,
            int depth) {
        this(spiderParam, valueGenerator, null, null, httpMessage, path, depth);
    }

    /**
     * Constructs a {@code ParseContext} with the given values.
     *
     * @param spiderParam the spider options, must not be {@code null}.
     * @param valueGenerator the value generator, must not be {@code null}.
     * @param context the context being used by/in the current spidering scan.
     * @param user the user being used by/in the current spidering scan.
     * @param httpMessage the message, must not be {@code null}.
     * @param path the path of the HTTP message.
     * @param depth the current depth of the parsing.
     * @throws NullPointerException if any of {@code spiderParam}, {@code valueGenerator}, or {@code
     *     httpMessage} is {@code null}.
     * @since 0.12.0
     */
    public ParseContext(
            SpiderParam spiderParam,
            ValueProvider valueGenerator,
            Context context,
            User user,
            HttpMessage httpMessage,
            String path,
            int depth) {
        this.spiderParam = Objects.requireNonNull(spiderParam);
        this.valueProvider = Objects.requireNonNull(valueGenerator);
        this.context = context;
        this.user = user;
        this.httpMessage = Objects.requireNonNull(httpMessage);
        this.path = path;
        this.depth = depth;
    }

    /**
     * Gets the spider options.
     *
     * @return the options, never {@code null}.
     */
    public SpiderParam getSpiderParam() {
        return spiderParam;
    }

    /**
     * Gets the value generator.
     *
     * @return the value generator, never {@code null}.
     * @deprecated (0.13.0) Use {@link #getValueProvider()} instead.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    public ValueGenerator getValueGenerator() {
        return new ValueGenerator() {

            @Override
            public String getValue(
                    URI uri,
                    String url,
                    String fieldId,
                    String defaultValue,
                    List<String> definedValues,
                    Map<String, String> envAttributes,
                    Map<String, String> fieldAttributes) {
                return valueProvider.getValue(
                        uri,
                        url,
                        fieldId,
                        defaultValue,
                        definedValues,
                        envAttributes,
                        fieldAttributes);
            }
        };
    }

    /**
     * Gets the value provider.
     *
     * @return the value provider, never {@code null}.
     * @since 0.13.0
     */
    public ValueProvider getValueProvider() {
        return valueProvider;
    }

    /**
     * Gets the context used by/in the spidering scan.
     *
     * @return the context, or {@code null}.
     * @since 0.12.0
     */
    public Context getContext() {
        return context;
    }

    /**
     * Gets the user used by/in the spidering scan.
     *
     * @return the user, or {@code null}.
     * @since 0.12.0
     */
    public User getUser() {
        return user;
    }

    /**
     * Gets the HTTP message.
     *
     * @return the message, never {@code null}.
     */
    public HttpMessage getHttpMessage() {
        return httpMessage;
    }

    /**
     * Gets the path of the HTTP message.
     *
     * @return the path, might be {@code null}.
     */
    public String getPath() {
        return path;
    }

    /**
     * Gets the current depth of the parsing.
     *
     * @return the depth.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Gets the URL of the HTTP message.
     *
     * @return the URL.
     */
    public String getBaseUrl() {
        if (baseUrl == null) {
            baseUrl = httpMessage.getRequestHeader().getURI().toString();
        }
        return baseUrl;
    }

    /**
     * Gets the {@code Source} with the response.
     *
     * @return the source.
     */
    public Source getSource() {
        if (source == null) {
            source = new Source(httpMessage.getResponseBody().toString());
        }
        return source;
    }
}
