package com.mcpgateway.domain.converter;

import jakarta.persistence.AttributeConverter;

import java.util.Locale;

/**
 * Bridges Java enum constants (upper case, idiomatic) to PostgreSQL enum labels
 * (lower case, as defined in {@code schema.sql}).
 *
 * <p>Concrete converters only declare the enum class; the conversion itself lives
 * here so a new enum never needs the logic copied again.
 *
 * <p>The JDBC url must carry {@code stringtype=unspecified} so PostgreSQL casts
 * the bound string to the target enum type instead of rejecting a varchar.
 *
 * @param <E> the enum being converted
 */
public abstract class LowerCaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected LowerCaseEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumType, dbData.toUpperCase(Locale.ROOT));
    }
}
