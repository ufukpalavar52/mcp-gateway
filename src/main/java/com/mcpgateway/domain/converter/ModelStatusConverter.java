package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.ModelStatus;
import jakarta.persistence.Converter;

/** Maps {@link ModelStatus} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class ModelStatusConverter extends LowerCaseEnumConverter<ModelStatus> {

    public ModelStatusConverter() {
        super(ModelStatus.class);
    }
}
