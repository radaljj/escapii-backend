package com.escapii.validation;

import com.escapii.model.DepartureAirport;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Polje mora biti kod postojećeg aerodroma polaska iz {@link DepartureAirport}.
 *
 * Zamena za raniji {@code @Pattern(regexp = "BEG|INI|...")} - regex je konstanta u
 * vreme kompajliranja, pa bi svaki nov aerodrom morao ručno da se dopiše na svakom
 * DTO-u. Ovako lista aerodroma živi samo na jednom mestu.
 *
 * Prazna vrednost se namerno propušta - to pokriva {@code @NotBlank}, da korisnik
 * ne bi dobio dve poruke o istoj grešci.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDepartureAirport.Validator.class)
public @interface ValidDepartureAirport {

    String message() default "Nepoznat aerodrom polaska";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidDepartureAirport, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) return true; // @NotBlank pokriva
            if (DepartureAirport.isValid(value)) return true;

            // Poruka nabraja trenutno dostupne aerodrome - raste sama sa enumom
            String allowed = Arrays.stream(DepartureAirport.values())
                    .map(DepartureAirport::code)
                    .collect(Collectors.joining(", "));
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Nepoznat aerodrom polaska (dostupni: " + allowed + ")").addConstraintViolation();
            return false;
        }
    }
}
