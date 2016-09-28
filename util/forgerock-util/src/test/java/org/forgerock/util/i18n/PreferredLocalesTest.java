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
 * Copyright 2015-2016 ForgeRock AS.
 */

package org.forgerock.util.i18n;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class PreferredLocalesTest {

    /**
     * Bundle defined in test resources that has a default set, an {@code fr} set, and an {@code fr_CA} set. The files
     * contain a single property, {@code locale}, that is set to {@code ROOT}, {@code French}
     * and {@code Canadian French} respectively.
     */
    static final String BUNDLE_NAME = "locales/bundle";

    @DataProvider
    public Object[][] localeExpectations() {
        return new Object[][] {
            { null, "ROOT" },
            { singletonList("fr"), "French" }, // as requested
            { asList("es", "de"), "ROOT" }, // No matching
            { asList("en", "fr"), "ROOT" }, // en supplied by default
            { asList("en", "fr", "en-US"), "French" }, // Default less desired than fr, no en
            { asList("zh", "de", "fr-FR"), "French" }, // no match for first 2
            { asList("fr-FR", "fr-CA", "fr"), "Canadian French" } // no fr-FR, fr less than fr-CA
        };
    }

    @Test(dataProvider = "localeExpectations")
    public void testGetBundle(List<String> localeLanguageTags, String expectedLocale) throws Exception {
        // Given
        List<Locale> localeList = null;
        if (localeLanguageTags != null) {
            localeList = new ArrayList<>(localeLanguageTags.size());
            for (String tag : localeLanguageTags) {
                localeList.add(Locale.forLanguageTag(tag));
            }
        }
        PreferredLocales locales = new PreferredLocales(localeList);

        // When
        ResourceBundle bundle = locales.getBundleInPreferredLocale(BUNDLE_NAME, getClass().getClassLoader());

        // Then
        assertThat(bundle.getString("locale")).isEqualTo(expectedLocale);
    }
}
