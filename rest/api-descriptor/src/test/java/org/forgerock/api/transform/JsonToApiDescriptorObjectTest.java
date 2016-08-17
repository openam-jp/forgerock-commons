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
package org.forgerock.api.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.api.enums.PatchOperation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.forgerock.api.enums.CountPolicy;
import org.forgerock.api.enums.CreateMode;
import org.forgerock.api.enums.QueryType;
import org.forgerock.api.models.ApiDescription;
import org.forgerock.api.models.Resource;
import org.forgerock.api.models.Schema;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.util.i18n.LocalizableString;
import org.forgerock.util.i18n.PreferredLocales;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/** Json to {@link ApiDescription} deserializer test */
public class JsonToApiDescriptorObjectTest {

    private static final LocalizableString DESCRIPTION = new LocalizableString(
            "Users can have devices, but the devices are their own resources.");

    private static final File[] EXAMPLE_FILES = new File("docs/examples").listFiles();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModules(new Json.JsonValueModule(), new Json.LocalizableStringModule());

    @Test
    public void subResourcesJsonToApiDescriptorPropertiesTest() throws IOException {
        File file = Paths.get("docs/examples/sub-resources.json").toFile();

        ApiDescription apiDescription = OBJECT_MAPPER.readValue(file, ApiDescription.class);

        assertThat(apiDescription.getDescription().toTranslatedString(new PreferredLocales()))
                .isEqualTo(DESCRIPTION.toTranslatedString(new PreferredLocales()));
        assertThat(apiDescription.getDefinitions().getNames()).hasSize(2);

        //schema assertions
        final Schema userDef = apiDescription.getDefinitions().get("user");
        assertThat(userDef.getReference()).isNull();
        assertThat(userDef.getSchema()).hasSize(5);
        assertThat(userDef.getSchema().get("description")
                .asString()).isEqualTo("User with device sub-resources");
        final JsonValue userProps = userDef.getSchema().get("properties");
        assertThat(userProps).hasSize(6);
        assertThat(userProps.get("_id")).hasSize(4);
        assertThat(userProps.get("_id").get("title").asString()).isEqualTo("Unique Identifier");
        assertThat(userProps.get("_rev")).hasSize(3);
        assertThat(userProps.get("_rev").get("title").asString()).isEqualTo("Revision Identifier");
        assertThat(userProps.get("uid")).hasSize(3);
        assertThat(userProps.get("uid").get("title").asString()).isEqualTo("User unique identifier");
        assertThat(userProps.get("name")).hasSize(3);
        assertThat(userProps.get("name").get("title").asString()).isEqualTo("User name");
        assertThat(userProps.get("password")).hasSize(3);
        assertThat(userProps.get("password").get("description").asString()).isEqualTo("Password of the user");
        assertThat(userProps.get("devices")).hasSize(6);
        assertThat(userProps.get("devices").get("items").get("$ref").asString()).isEqualTo("#/definitions/device");

        final Schema deviceDef = apiDescription.getDefinitions().get("device");
        assertThat(deviceDef.getReference()).isNull();
        assertThat(deviceDef.getSchema()).hasSize(5);
        assertThat(deviceDef.getSchema().get("description").asString()).isEqualTo("Device");
        final JsonValue deviceProps = deviceDef.getSchema().get("properties");
        assertThat(deviceProps).hasSize(7);
        assertThat(deviceProps.get("_id")).hasSize(4);
        assertThat(deviceProps.get("_id").get("title").asString()).isEqualTo("Unique Identifier");
        assertThat(deviceProps.get("_rev")).hasSize(3);
        assertThat(deviceProps.get("_rev").get("title").asString()).isEqualTo("Revision Identifier");
        assertThat(deviceProps.get("did")).hasSize(2);
        assertThat(deviceProps.get("did").get("title").asString()).isEqualTo("Unique Identifier of the device");
        assertThat(deviceProps.get("name")).hasSize(2);
        assertThat(deviceProps.get("name").get("title").asString()).isEqualTo("Device name");
        assertThat(deviceProps.get("type")).hasSize(2);
        assertThat(deviceProps.get("type").get("title").asString()).isEqualTo("Device type");
        assertThat(deviceProps.get("stolen")).hasSize(3);
        assertThat(deviceProps.get("stolen").get("title").asString()).isEqualTo("Stolen flag");
        assertThat(deviceProps.get("rollOutDate")).hasSize(3);
        assertThat(deviceProps.get("rollOutDate").get("title").asString()).isEqualTo("Roll-out date");

        //services
        assertThat(apiDescription.getServices().getNames()).hasSize(4);

        final Resource devices10Service = apiDescription.getServices().get("devices:1.0");
        assertThat(devices10Service.getResourceSchema().getReference().getValue()).isEqualTo("#/definitions/device");
        assertThat(devices10Service.getDescription()).isNotNull();
        assertThat(devices10Service.getCreate().getMode()).isEqualTo(CreateMode.ID_FROM_SERVER);
        assertThat(devices10Service.getCreate().getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getCreate().getApiErrors()).hasSize(2);
        assertThat(devices10Service.getRead()).isNull();
        assertThat(devices10Service.getUpdate()).isNull();
        assertThat(devices10Service.getDelete()).isNull();
        assertThat(devices10Service.getPatch()).isNull();
        assertThat(devices10Service.getActions()).hasSize(0);
        assertThat(devices10Service.getQueries()).hasSize(1);
        assertThat(devices10Service.getQueries()[0].getType()).isEqualTo(QueryType.FILTER);
        assertThat(devices10Service.getQueries()[0].getPagingModes()).hasSize(2);
        assertThat(devices10Service.getQueries()[0].getCountPolicies()).hasSize(1);
        assertThat(devices10Service.getQueries()[0].getCountPolicies()).containsOnly(CountPolicy.NONE);
        assertThat(devices10Service.getQueries()[0].getQueryableFields()).hasSize(5);
        assertThat(devices10Service.getQueries()[0].getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getQueries()[0].getApiErrors()).hasSize(2);
        assertThat(devices10Service.getQueries()[0].getParameters()).isNull();
        assertThat(devices10Service.getQueries()[0].getStability()).isNull();

        assertThat(devices10Service.getItems().getCreate().getMode()).isEqualTo(CreateMode.ID_FROM_CLIENT);
        assertThat(devices10Service.getItems().getCreate().getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getItems().getCreate().getApiErrors()).hasSize(2);
        assertThat(devices10Service.getItems().getRead().getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getItems().getRead().getApiErrors()).hasSize(3);
        assertThat(devices10Service.getItems().getUpdate().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("Update a device");
        assertThat(devices10Service.getItems().getUpdate().getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getItems().getUpdate().getApiErrors()).hasSize(2);
        assertThat(devices10Service.getItems().getDelete().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("Delete a device");
        assertThat(devices10Service.getItems().getDelete().getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getItems().getDelete().getApiErrors()).hasSize(2);
        assertThat(devices10Service.getItems().getPatch().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("Patch a device");
        assertThat(devices10Service.getItems().getPatch().getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getItems().getPatch().getApiErrors()).hasSize(2);
        assertThat(devices10Service.getItems().getPatch().getOperations()).hasSize(2);
        assertThat(devices10Service.getItems().getPatch().getOperations()).containsOnly(ADD, REMOVE);
        assertThat(devices10Service.getItems().getActions()).hasSize(1);
        assertThat(devices10Service.getItems().getActions()[0].getName()).isEqualTo("markAsStolen");
        assertThat(devices10Service.getItems().getActions()[0].getSupportedLocales()).hasSize(2);
        assertThat(devices10Service.getItems().getActions()[0].getApiErrors()).hasSize(3);

        assertThat(apiDescription.getServices().getNames()).hasSize(4);
        final Resource device20Service = apiDescription.getServices().get("devices:2.0");
        assertThat(device20Service.getResourceSchema().getReference().getValue()).isEqualTo("#/definitions/device");
        assertThat(device20Service.getDescription()).isNotNull();
        assertThat(device20Service.getCreate().getMode()).isEqualTo(CreateMode.ID_FROM_SERVER);
        assertThat(device20Service.getCreate().getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getCreate().getApiErrors()).hasSize(2);
        assertThat(device20Service.getRead()).isNull();
        assertThat(device20Service.getUpdate()).isNull();
        assertThat(device20Service.getDelete()).isNull();
        assertThat(device20Service.getPatch()).isNull();
        assertThat(device20Service.getActions()).hasSize(0);
        assertThat(device20Service.getQueries()).hasSize(1);
        assertThat(device20Service.getQueries()[0].getType()).isEqualTo(QueryType.FILTER);
        assertThat(device20Service.getQueries()[0].getPagingModes()).hasSize(2);
        assertThat(device20Service.getQueries()[0].getCountPolicies()).hasSize(1);
        assertThat(device20Service.getQueries()[0].getCountPolicies()).containsOnly(CountPolicy.NONE);
        assertThat(device20Service.getQueries()[0].getQueryableFields()).hasSize(5);
        assertThat(device20Service.getQueries()[0].getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getQueries()[0].getApiErrors()).hasSize(2);
        assertThat(device20Service.getQueries()[0].getParameters()).isNull();
        assertThat(device20Service.getQueries()[0].getStability()).isNull();

        assertThat(device20Service.getItems().getCreate().getMode()).isEqualTo(CreateMode.ID_FROM_CLIENT);
        assertThat(device20Service.getItems().getCreate().getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getItems().getCreate().getApiErrors()).hasSize(2);
        assertThat(device20Service.getItems().getRead().getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getItems().getRead().getApiErrors()).hasSize(3);
        assertThat(device20Service.getItems().getUpdate().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("Update a device");
        assertThat(device20Service.getItems().getUpdate().getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getItems().getUpdate().getApiErrors()).hasSize(2);
        assertThat(device20Service.getItems().getDelete().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("Delete a device");
        assertThat(device20Service.getItems().getDelete().getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getItems().getDelete().getApiErrors()).hasSize(2);
        assertThat(device20Service.getItems().getPatch().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("Patch a device");
        assertThat(device20Service.getItems().getPatch().getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getItems().getPatch().getApiErrors()).hasSize(2);
        assertThat(device20Service.getItems().getPatch().getOperations()).hasSize(2);
        assertThat(device20Service.getItems().getPatch().getOperations()).containsOnly(ADD, REMOVE);
        assertThat(device20Service.getItems().getActions()).hasSize(2);
        assertThat(device20Service.getItems().getActions()[0].getName()).isEqualTo("markAsStolen");
        assertThat(device20Service.getItems().getActions()[0].getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getItems().getActions()[0].getApiErrors()).hasSize(3);
        assertThat(device20Service.getItems().getActions()[1].getName()).isEqualTo("rollOut");
        assertThat(device20Service.getItems().getActions()[1].getSupportedLocales()).hasSize(2);
        assertThat(device20Service.getItems().getActions()[1].getApiErrors()).hasSize(3);

        final Resource users10Service = apiDescription.getServices().get("users:1.0");
        assertThat(users10Service.getResourceSchema().getReference().getValue()).isEqualTo("#/definitions/user");
        assertThat(users10Service.getDescription()).isNotNull();
        assertThat(users10Service.getCreate().getMode()).isEqualTo(CreateMode.ID_FROM_SERVER);
        assertThat(users10Service.getCreate().getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getCreate().getApiErrors()).hasSize(2);
        assertThat(users10Service.getRead()).isNull();
        assertThat(users10Service.getUpdate()).isNull();
        assertThat(users10Service.getDelete()).isNull();
        assertThat(users10Service.getPatch()).isNull();
        assertThat(users10Service.getActions()).hasSize(0);
        assertThat(users10Service.getQueries()).hasSize(1);
        assertThat(users10Service.getQueries()[0].getType()).isEqualTo(QueryType.FILTER);
        assertThat(users10Service.getQueries()[0].getPagingModes()).hasSize(2);
        assertThat(users10Service.getQueries()[0].getCountPolicies()).hasSize(1);
        assertThat(users10Service.getQueries()[0].getCountPolicies()).containsOnly(CountPolicy.NONE);
        assertThat(users10Service.getQueries()[0].getQueryableFields()).hasSize(3);
        assertThat(users10Service.getQueries()[0].getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getQueries()[0].getApiErrors()).hasSize(2);
        assertThat(users10Service.getQueries()[0].getParameters()).isNull();
        assertThat(users10Service.getQueries()[0].getStability()).isNull();

        assertThat(users10Service.getItems().getCreate().getMode()).isEqualTo(CreateMode.ID_FROM_CLIENT);
        assertThat(users10Service.getItems().getCreate().getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getItems().getCreate().getApiErrors()).hasSize(2);
        assertThat(users10Service.getItems().getRead().getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getItems().getRead().getApiErrors()).hasSize(3);
        assertThat(users10Service.getItems().getUpdate().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("User update operation");
        assertThat(users10Service.getItems().getUpdate().getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getItems().getUpdate().getApiErrors()).hasSize(2);
        assertThat(users10Service.getItems().getDelete().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("User delete operation");
        assertThat(users10Service.getItems().getDelete().getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getItems().getDelete().getApiErrors()).hasSize(2);
        assertThat(users10Service.getItems().getPatch().getDescription()
                .toTranslatedString(new PreferredLocales()))
                .isEqualTo("User patch operation");
        assertThat(users10Service.getItems().getPatch().getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getItems().getPatch().getApiErrors()).hasSize(2);
        assertThat(users10Service.getItems().getPatch().getOperations()).hasSize(2);
        assertThat(users10Service.getItems().getPatch().getOperations()).containsOnly(ADD, REMOVE);
        assertThat(users10Service.getItems().getActions()).hasSize(1);
        assertThat(users10Service.getItems().getActions()[0].getName()).isEqualTo("resetPassword");
        assertThat(users10Service.getItems().getActions()[0].getSupportedLocales()).hasSize(2);
        assertThat(users10Service.getItems().getActions()[0].getApiErrors()).hasSize(3);

        //errors
        assertThat(apiDescription.getErrors().getNames()).hasSize(2);
        assertThat(apiDescription.getErrors().getNames().contains("badRequest")).isTrue();
        assertThat(apiDescription.getErrors().getNames().contains("unauthorized")).isTrue();

        //paths
        assertThat(apiDescription.getPaths().getNames()).hasSize(2);
        assertThat(apiDescription.getPaths().get("/users").getVersions()).hasSize(2);
        assertThat(apiDescription.getPaths().get("/admins").getVersions()).hasSize(1);

    }

    @Test(dataProvider = "exampleFilesProvider")
    public void testExample(File example) throws Exception {
        System.out.println(example.getName());
        ApiDescription description = OBJECT_MAPPER.readValue(example, ApiDescription.class);
        String jsonFromDescription = writeApiDescriptiontoJson(description);
        ApiDescription descriptionFromJsonString = OBJECT_MAPPER.readValue(jsonFromDescription, ApiDescription.class);
        assertThat(description).isEqualTo(descriptionFromJsonString);

        Object descriptionObjectFromFile = OBJECT_MAPPER.readValue(example, Object.class);
        Object descriptionObjectFromJsonString = OBJECT_MAPPER.readValue(jsonFromDescription, Object.class);

        assertThat(descriptionObjectFromFile).isEqualTo(descriptionObjectFromJsonString);
    }

    @DataProvider
    public Object[][] exampleFilesProvider() throws Exception {
        Object[][] retVal = new Object[EXAMPLE_FILES.length][1];
        for (int i = 0; i < EXAMPLE_FILES.length; i++) {
            retVal[i] = new Object[] {EXAMPLE_FILES[i]};
        }
        return retVal;
    }

    private String writeApiDescriptiontoJson(ApiDescription apiDescription) throws IOException {
        ObjectWriter ow = OBJECT_MAPPER.writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(apiDescription);
    }
}
