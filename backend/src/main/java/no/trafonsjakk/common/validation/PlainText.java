package no.trafonsjakk.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Text a person typed: no control characters except tab and line breaks. PostgreSQL can't store
 * NUL at all, and other control characters only cause trouble when displayed.
 */
@Documented
@Constraint(validatedBy = PlainText.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlainText {

    String message() default "contains characters that aren't allowed";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<PlainText, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || isPlain(value);
        }

        public static boolean isPlain(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if ((c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0x7f) {
                    return false;
                }
            }
            return true;
        }
    }
}
