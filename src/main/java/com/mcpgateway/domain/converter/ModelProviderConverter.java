package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.ModelProvider;
import jakarta.persistence.Converter;

/** Maps {@link ModelProvider} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class ModelProviderConverter extends LowerCaseEnumConverter<ModelProvider> {

    public ModelProviderConverter() {
        super(ModelProvider.class);
    }
}
