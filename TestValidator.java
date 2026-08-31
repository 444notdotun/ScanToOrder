import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.Set;

public class TestValidator {
    public static class Dto {
        @Min(11)
        public String phone;
    }
    public static void main(String[] args) {
        try {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            Validator validator = factory.getValidator();
            Dto d = new Dto();
            d.phone = "11";
            Set<ConstraintViolation<Dto>> violations = validator.validate(d);
            System.out.println("Violations: " + violations.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
