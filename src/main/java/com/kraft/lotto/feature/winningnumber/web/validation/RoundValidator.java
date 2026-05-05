package com.kraft.lotto.feature.winningnumber.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RoundValidator implements ConstraintValidator<ValidRound, CharSequence> {

    private int min;
    private int max;
    private boolean allowNull;

    @Override
    public void initialize(ValidRound constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.allowNull = constraintAnnotation.allowNull();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return allowNull;
        }
        String text = value.toString();
        if (!text.matches("^[0-9]{1,6}$")) {
            return false;
        }
        int round;
        try {
            round = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return false;
        }
        return round >= min && round <= max;
    }
}
