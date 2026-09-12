package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.ActionKind;
import jakarta.persistence.Converter;

/** Maps {@link ActionKind} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class ActionKindConverter extends LowerCaseEnumConverter<ActionKind> {

    public ActionKindConverter() {
        super(ActionKind.class);
    }
}
