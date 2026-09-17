package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.DefinitionAccess;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DefinitionAccessConverter extends LowerCaseEnumConverter<DefinitionAccess> {

    public DefinitionAccessConverter() {
        super(DefinitionAccess.class);
    }
}
