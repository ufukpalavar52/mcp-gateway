package com.mcpgateway.domain.converter;

import com.mcpgateway.domain.enums.UserStatus;
import jakarta.persistence.Converter;

/** Maps {@link UserStatus} to its PostgreSQL enum label. */
@Converter(autoApply = true)
public class UserStatusConverter extends LowerCaseEnumConverter<UserStatus> {

    public UserStatusConverter() {
        super(UserStatus.class);
    }
}
