package com.example.claims.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A US National Provider Identifier: 10 digits with a valid CMS Luhn check digit. Null is treated as valid. */
@Documented
@Constraint(validatedBy = NpiValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Npi {

    String message() default "must be a valid 10-digit NPI";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
