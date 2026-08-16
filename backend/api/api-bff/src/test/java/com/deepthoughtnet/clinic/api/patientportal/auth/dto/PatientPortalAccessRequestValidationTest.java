package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PatientPortalAccessRequestValidationTest {
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.afterPropertiesSet();
        validator = factoryBean;
    }

    @Test
    void accessLoginRequestValidatesPhoneAndAccessCodeFormats() {
        Set<ConstraintViolation<PatientPortalAccessLoginRequest>> violations = validator.validate(
                new PatientPortalAccessLoginRequest("123@@@", "1234", null)
        );

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("mobile", "accessCode");
    }

    @Test
    void accessRequestSubmitValidatesMandatoryFieldsAndContextSlug() {
        Set<ConstraintViolation<PatientPortalAccessRequestSubmitRequest>> violations = validator.validate(
                new PatientPortalAccessRequestSubmitRequest(
                        " ",
                        "123@@@",
                        "not-an-email",
                        "x".repeat(501),
                        new PatientPortalOtpContext(null, "123@@@", null, null, null)
                )
        );

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("fullName", "mobile", "email", "note", "context.clinicSlug");
    }
}
