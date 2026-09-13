package com.escapii.passport;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA konverter za {@code PassengerInfo.passportNumber}: u bazi šifrat, u aplikaciji
 * otvoren broj - pa admin panel, mejlovi i sve ostalo rade kao i pre.
 */
@Converter
public class PassportNumberConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plain) {
        return PassportCrypto.toStored(plain);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return PassportCrypto.decrypt(stored);
    }
}
