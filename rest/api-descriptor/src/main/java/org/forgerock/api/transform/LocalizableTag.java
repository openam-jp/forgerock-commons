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

import org.forgerock.util.i18n.LocalizableString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.models.Tag;

/**
 * Localizable {@link io.swagger.models.Tag}.
 */
public class LocalizableTag extends Tag implements LocalizableDescription<Tag> {
    private LocalizableString description;
    private LocalizableString name;

    @Override
    public LocalizableTag description(LocalizableString desc) {
        this.description = desc;
        return this;
    }

    @Override
    public LocalizableTag description(String description) {
        setDescription(description);
        return this;
    }

    @Override
    public void setDescription(String description) {
        super.setName(description);
        this.description = new LocalizableString(description);
    }

    @Override
    public LocalizableString getLocalizableDescription() {
        return description;
    }

    /**
     * Sets the name of the Tag.
     *
     * @param name the tag name
     * @return the localizable tag
     */
    public LocalizableTag name(LocalizableString name) {
        super.setName(name.toString());
        this.name = name;
        return this;
    }

    @Override
    public LocalizableTag name(String name) {
        setName(name);
        return this;
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        this.name = new LocalizableString(name);
    }

    /**
     * Returns the name of a Tag, a LocalizableString.
     *
     * @return the name, a LocalizableString
     */
    @JsonProperty("name")
    public LocalizableString getLocalizableName() {
        return name;
    }

    @Override
    @JsonIgnore
    public String getName() {
        return super.getName();
    }
}
