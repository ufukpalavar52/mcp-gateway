package com.mcpgateway.mapper;

import com.mcpgateway.domain.entity.AiModel;
import com.mcpgateway.dto.response.AiModelResponse;
import org.springframework.stereotype.Component;

/** Entity to response translation for model connections. */
@Component
public class AiModelMapper {

    public AiModelResponse toResponse(AiModel model, long definitionCount) {
        return new AiModelResponse(
                model.getId(),
                model.getName(),
                model.getProvider(),
                model.getModelId(),
                model.getEndpoint(),
                model.getApiKeySecret() == null ? null : model.getApiKeySecret().getId(),
                model.getApiKeySecret() == null ? null : model.getApiKeySecret().getName(),
                model.getApiKeySecret() != null,
                model.getParams(),
                model.getHealth(),
                model.isEnabled(),
                model.getNotes(),
                definitionCount,
                model.getUpdatedAt());
    }
}
