package com.scantoorder.scantoorder.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ChargeCalculatorTest {

    private ChargeCalculator calculator(String percent, String bearer) {
        ChargeCalculator calculator = new ChargeCalculator();
        ReflectionTestUtils.setField(calculator, "percent", new BigDecimal(percent));
        ReflectionTestUtils.setField(calculator, "feeBearer", bearer);
        calculator.validate();
        return calculator;
    }

    @Test
    void testThatTheChargeIsThePercentageOfTheAmountInKobo() {
        ChargeCalculator.Split split = calculator("2.0", "account").split(150_000);
        assertEquals(3_000, split.platelyChargeKobo());
        assertEquals(147_000, split.tenantAmountKobo());
        assertEquals(150_000, split.amountKobo());
    }

    @Test
    void testThatTheChargeIsRoundedDownToTheKoboAndNeverUp() {
        // 2% of 12345 kobo is 246.9: the restaurant keeps the odd fraction.
        ChargeCalculator.Split split = calculator("2.0", "account").split(12_345);
        assertEquals(246, split.platelyChargeKobo());
        assertEquals(12_099, split.tenantAmountKobo());
        assertEquals(1, calculator("1.5", "account").chargeKobo(99));
        assertEquals(0, calculator("1.5", "account").chargeKobo(66));
    }

    @Test
    void testThatAVerySmallPaymentCanHaveAZeroKoboCharge() {
        ChargeCalculator.Split split = calculator("2.0", "account").split(49);
        assertEquals(0, split.platelyChargeKobo());
        assertEquals(49, split.tenantAmountKobo());
        assertEquals(0, calculator("2.0", "account").chargeKobo(0));
    }

    @Test
    void testThatALargeOrderIsCalculatedExactly() {
        // NGN 10,000,000.00 is 1,000,000,000 kobo.
        ChargeCalculator.Split split = calculator("2.0", "account").split(1_000_000_000L);
        assertEquals(20_000_000L, split.platelyChargeKobo());
        assertEquals(980_000_000L, split.tenantAmountKobo());
        ChargeCalculator.Split huge = calculator("2.5", "account").split(900_000_000_000_000L);
        assertEquals(22_500_000_000_000L, huge.platelyChargeKobo());
        assertEquals(huge.amountKobo(), huge.platelyChargeKobo() + huge.tenantAmountKobo());
    }

    @Test
    void testThatThePartsAlwaysAddUpToTheAmount() {
        ChargeCalculator calculator = calculator("2.37", "account");
        for (long amount : new long[]{1, 7, 99, 100, 101, 12_345, 987_654_321}) {
            ChargeCalculator.Split split = calculator.split(amount);
            assertEquals(amount, split.platelyChargeKobo() + split.tenantAmountKobo());
            assertTrue(split.platelyChargeKobo() <= amount * 2.37 / 100);
        }
    }

    @Test
    void testThatAZeroOrFullPercentageWorks() {
        assertEquals(0, calculator("0", "account").chargeKobo(50_000));
        assertEquals(50_000, calculator("100", "account").chargeKobo(50_000));
    }

    @Test
    void testThatANegativeAmountIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> calculator("2.0", "account").chargeKobo(-1));
    }

    @Test
    void testThatABadPercentageOrBearerStopsTheApp() {
        assertThrows(IllegalStateException.class, () -> calculator("-0.1", "account"));
        assertThrows(IllegalStateException.class, () -> calculator("100.1", "account"));
        assertThrows(IllegalStateException.class, () -> calculator("2.0", "everyone"));
        assertEquals("subaccount", calculator("2.0", "subaccount").feeBearer());
    }
}
