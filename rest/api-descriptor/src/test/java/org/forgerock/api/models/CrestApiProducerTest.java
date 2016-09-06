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

package org.forgerock.api.models;

import static org.forgerock.api.models.Read.read;
import static org.forgerock.api.models.Update.update;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.api.CrestApiProducer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CrestApiProducerTest {

    public static final String TESTERRORID = "testerrorid";

    @DataProvider(name = "apidesclist")
    private Object [][] generateApiDescriptorList() {
        List<ApiDescription> apiDescList = new ArrayList<>();

        ApiDescription apiDesc = generateBaseApiDesc("fake1");
        apiDesc.addError(TESTERRORID, generateApiError("desc1"));
        ApiDescription apiDesc2 = generateBaseApiDesc("fake2");
        apiDesc2.addError(TESTERRORID, generateApiError("desc2"));

        apiDescList.add(apiDesc);
        apiDescList.add(apiDesc2);

        return new Object [][] {{apiDescList}};
    }

    @Test(expectedExceptions = IllegalStateException.class, dataProvider = "apidesclist")
    public void testMerge(List<ApiDescription> apiDescriptionList) {
        CrestApiProducer cap = new CrestApiProducer("fakemergedid", "fakemergedversion");
        cap.merge(apiDescriptionList);
    }

    private ApiDescription generateBaseApiDesc(String id) {
        return ApiDescription.apiDescription().id(id).version("v").paths(
            Paths.paths().put(id, VersionedPath.versionedPath().put(
                VersionedPath.UNVERSIONED,
                Resource.resource().title("faketitle")
                    .description("fakedesc")
                    .mvccSupported(false)
                    .resourceSchema(org.forgerock.api.models.Schema.schema()
                        .schema(json(object(field("type", "object")))).build())
                        .read(read().error(generateApiErrorReference()).build())
                        .update(update().error(generateApiErrorReference()).build())
                    .build())
                .build())
            .build())
        .build();
    }

    private ApiError generateApiErrorReference() {
        return ApiError.apiError().reference(Reference.reference().value("#/errors/" + TESTERRORID).build()).build();
    }


    private ApiError generateApiError(String desc) {
        return ApiError.apiError().code(1).description(TESTERRORID + desc).build();
    }

}
