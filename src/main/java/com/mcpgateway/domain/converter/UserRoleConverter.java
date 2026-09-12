package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.UserRole;
import jakarta.persistence.Converter;

/** Maps {@link UserRole} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class UserRoleConverter extends LowerCaseEnumConverter<UserRole> {

    public UserRoleConverter() {
        super(UserRole.class);
    }
}
