package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.SecretKind;
import jakarta.persistence.Converter;

/** Maps {@link SecretKind} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class SecretKindConverter extends LowerCaseEnumConverter<SecretKind> {

    public SecretKindConverter() {
        super(SecretKind.class);
    }
}
