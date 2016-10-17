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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.json.resource.http;

import static io.swagger.models.Scheme.HTTP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.api.models.ApiDescription.apiDescription;
import static org.forgerock.api.models.Paths.paths;
import static org.forgerock.api.models.Read.read;
import static org.forgerock.api.models.Resource.resource;
import static org.forgerock.api.models.Schema.schema;
import static org.forgerock.api.models.VersionedPath.UNVERSIONED;
import static org.forgerock.api.models.VersionedPath.versionedPath;
import static org.forgerock.json.resource.Applications.simpleCrestApplication;
import static org.forgerock.json.test.assertj.AssertJJsonValueAssert.assertThat;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.forgerock.util.test.assertj.AssertJPromiseAssert.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

import java.util.Collections;
import java.util.List;

import org.forgerock.api.models.ApiDescription;
import org.forgerock.http.ApiProducer;
import org.forgerock.http.protocol.Entity;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.swagger.SwaggerApiProducer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.descriptor.Describable;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.swagger.models.Info;
import io.swagger.models.Swagger;

public class HttpAdapterTest {

    public static final ApiDescription API_DESCRIPTION = apiDescription().id("test:descriptor").version("1.0")
            .paths(paths().put("/mypath", versionedPath().put(UNVERSIONED, resource()
                    .resourceSchema(schema().type(String.class).build())
                    .title("Fred")
                    .mvccSupported(false)
                    .read(read().build())
                    .build()).build()).build())
            .build();

    @Mock
    private DescribableConnection connection;
    private HttpAdapter adapter;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.initMocks(this);
        adapter = new HttpAdapter(simpleCrestApplication(new ConnectionFactory() {
            @Override
            public void close() {

            }

            @Override
            public Connection getConnection() throws ResourceException {
                return connection;
            }

            @Override
            public Promise<Connection, ResourceException> getConnectionAsync() {
                return newResultPromise((Connection) connection);
            }
        }, "frapi:test", "1.0"), null);
    }

    @Test
    public void testHandleApiRequest() throws Exception {
        // Given
        given(connection.handleApiRequest(any(Context.class), any(org.forgerock.json.resource.Request.class)))
                .willReturn(API_DESCRIPTION);
        Request request = new Request().setMethod("GET").setUri("/test?_crestapi");
        AttributesContext context = new AttributesContext(new RootContext());

        // When
        Promise<Response, NeverThrowsException> result = adapter.handle(context, request);

        // Then
        assertThat(result).succeeded();
        Entity entity = result.get().getEntity();
        assertThat(JsonValue.json(entity.getJson())).isObject().stringAt("id").isEqualTo("test:descriptor");
        assertThat(entity.getString()).startsWith("{\"id\":\"test:descriptor\",\"version\":\"1.0\",");
    }

    @Test
    public void testHandleApiRequestWithPrettyPrinting() throws Exception {
        // Given
        given(connection.handleApiRequest(any(Context.class), any(org.forgerock.json.resource.Request.class)))
                .willReturn(API_DESCRIPTION);
        Request request = new Request().setMethod("GET").setUri("/test?_crestapi&_prettyPrint=true");
        AttributesContext context = new AttributesContext(new RootContext());

        // When
        Promise<Response, NeverThrowsException> result = adapter.handle(context, request);

        // Then
        assertThat(result).succeeded();
        Entity entity = result.get().getEntity();
        assertThat(JsonValue.json(entity.getJson())).isObject().stringAt("id").isEqualTo("test:descriptor");
        assertThat(entity.getString()).startsWith("{\n  \"id\" : \"test:descriptor\",\n  \"version\" : \"1.0\",\n");
    }

    @Test
    public void testHandleApiRequestForSwagger() throws Exception {
        // Given
        given(connection.api(any(ApiProducer.class))).willReturn(API_DESCRIPTION);
        given(connection.handleApiRequest(any(Context.class), any(org.forgerock.json.resource.Request.class)))
                .willReturn(API_DESCRIPTION);
        Request request = new Request();
        AttributesContext context = new AttributesContext(new RootContext());
        adapter.api(new SwaggerApiProducer(new Info(), "/base/path", "localhost:8080", HTTP));

        // When
        Swagger swagger = adapter.handleApiRequest(context, request);

        // Then
        assertThat(swagger).isNotNull();
        assertThat(swagger.getPaths()).containsKey("/mypath");
        assertThat(swagger.getBasePath()).isEqualTo("/base/path");
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        ArgumentCaptor<org.forgerock.json.resource.Request> requestCaptor
                = ArgumentCaptor.forClass(org.forgerock.json.resource.Request.class);
        verify(connection).handleApiRequest(contextCaptor.capture(), requestCaptor.capture());
        Context apiRequestContext = contextCaptor.getValue();
        assertThat(apiRequestContext.containsContext(UriRouterContext.class)).isTrue();
        assertThat(apiRequestContext.asContext(UriRouterContext.class).getRemainingUri()).isEqualTo("");
        org.forgerock.json.resource.Request apiRequest = requestCaptor.getValue();
        assertThat(apiRequest.getResourcePath()).isEqualTo("");
    }

    @Test
    public void testHandleApiRequestForSwaggerAtSubpath() throws Exception {
        // Given
        given(connection.api(any(ApiProducer.class))).willReturn(API_DESCRIPTION);
        given(connection.handleApiRequest(any(Context.class), any(org.forgerock.json.resource.Request.class)))
                .willReturn(API_DESCRIPTION);
        Request request = new Request();
        UriRouterContext context = new UriRouterContext(new AttributesContext(new RootContext()),
                "", "subpath", Collections.<String, String>emptyMap());
        adapter.api(new SwaggerApiProducer(new Info(), "/base/path", "localhost:8080", HTTP));

        // When
        Swagger swagger = adapter.handleApiRequest(context, request);

        // Then
        assertThat(swagger).isNotNull();
        assertThat(swagger.getPaths()).containsKey("/subpath/mypath");
        assertThat(swagger.getBasePath()).isEqualTo("/base/path");
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        ArgumentCaptor<org.forgerock.json.resource.Request> requestCaptor
                = ArgumentCaptor.forClass(org.forgerock.json.resource.Request.class);
        verify(connection, times(2)).handleApiRequest(contextCaptor.capture(), requestCaptor.capture());
        List<Context> contexts = contextCaptor.getAllValues();
        Context apiRequestContext = contexts.get(contexts.size() - 1);
        assertThat(apiRequestContext.containsContext(UriRouterContext.class)).isTrue();
        assertThat(apiRequestContext.asContext(UriRouterContext.class).getRemainingUri()).isEqualTo("subpath");
        org.forgerock.json.resource.Request apiRequest = requestCaptor.getAllValues().get(contexts.size() - 1);
        assertThat(apiRequest.getResourcePath()).isEqualTo("subpath");
    }

    private interface DescribableConnection extends Connection,
            Describable<ApiDescription, org.forgerock.json.resource.Request> {
        // for mocking
    }

}