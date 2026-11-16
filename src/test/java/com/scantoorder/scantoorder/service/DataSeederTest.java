package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.DataSeeder;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.respond.SubaccountData;
import com.scantoorder.scantoorder.exception.PaymentGatewayUnavailableException;
import com.scantoorder.scantoorder.exception.PayoutAccountRejectedException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class DataSeederTest {

    @Autowired
    private DataSeeder dataSeeder;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private ChargeCalculator chargeCalculator;
    @MockitoBean
    private PaystackClient paystackClient;
    @Value("${app.seed.manager-password}")
    private String managerPassword;

    private static final String CORNER = "The Corner Table (demo)";
    private static final String MAMA = "Mama Put Express (demo)";

    // The demo restaurants start without a subaccount, as they do when Paystack is unreachable.
    @BeforeEach
    void withoutSubaccounts() {
        for (String slug : List.of(DataSeeder.CORNER_TABLE_SLUG, DataSeeder.MAMA_PUT_SLUG)) {
            tenantRepo.findBySlug(slug).ifPresent(tenant -> {
                tenant.setPaystackSubaccountCode(null);
                tenantRepo.save(tenant);
            });
        }
        reset(paystackClient);
    }

    private String codeOf(String slug) {
        return tenantRepo.findBySlug(slug).orElseThrow().getPaystackSubaccountCode();
    }

    @Test
    void testThatAManagerIsSeededWithTheConfiguredPassword() {
        Worker manager = workerRepo.findByUsername(DataSeeder.MANAGER_USERNAME).orElseThrow();
        assertEquals(WorkerRole.MANAGER, manager.getRole());
        assertTrue(passwordEncoder.matches(managerPassword, manager.getPassword()));
    }

    @Test
    void testThatSeedingTwiceDoesNotDuplicateTheManagerOrTheMenu() {
        dataSeeder.run();
        long workers = workerRepo.count();
        long categories = categoryRepository.count();
        long items = itemRepo.count();
        dataSeeder.run();
        assertEquals(workers, workerRepo.count());
        assertEquals(categories, categoryRepository.count());
        assertEquals(items, itemRepo.count());
    }

    @Test
    void testThatADemoSubaccountPaystackAlreadyHasIsReusedAndNoTwinIsCreated() {
        when(paystackClient.listSubaccounts()).thenReturn(List.of(
                new SubaccountData("ACCT_corner01", CORNER), new SubaccountData("ACCT_mama0001", MAMA), new SubaccountData("ACCT_other001", "Somebody Else")));

        dataSeeder.run();

        assertEquals("ACCT_corner01", codeOf(DataSeeder.CORNER_TABLE_SLUG));
        assertEquals("ACCT_mama0001", codeOf(DataSeeder.MAMA_PUT_SLUG));
        verify(paystackClient, never()).createSubaccount(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testThatAMissingDemoSubaccountIsCreatedWithTheFixedTestBankAccount() {
        when(paystackClient.listSubaccounts()).thenReturn(List.of(new SubaccountData("ACCT_corner01", CORNER)));
        when(paystackClient.createSubaccount(eq(MAMA), anyString(), anyString(), any())).thenReturn("ACCT_made0001");

        dataSeeder.run();

        assertEquals("ACCT_corner01", codeOf(DataSeeder.CORNER_TABLE_SLUG));
        assertEquals("ACCT_made0001", codeOf(DataSeeder.MAMA_PUT_SLUG));
        verify(paystackClient, times(1)).createSubaccount(anyString(), anyString(), anyString(), any());
        verify(paystackClient).createSubaccount(MAMA, "057", "0000000000", chargeCalculator.percent());
    }

    @Test
    void testThatPaystackRefusingTheDemoSubaccountsStillStartsTheAppWithoutThem() {
        when(paystackClient.listSubaccounts()).thenReturn(List.of());
        when(paystackClient.createSubaccount(anyString(), anyString(), anyString(), any()))
                .thenThrow(new PayoutAccountRejectedException("We cannot add subaccount at the moment"));

        assertDoesNotThrow(() -> dataSeeder.run());

        assertNull(codeOf(DataSeeder.CORNER_TABLE_SLUG));
        assertNull(codeOf(DataSeeder.MAMA_PUT_SLUG));
        assertTrue(tenantRepo.findBySlug(DataSeeder.CORNER_TABLE_SLUG).isPresent());
        assertTrue(workerRepo.findByUsername(DataSeeder.MANAGER_USERNAME).isPresent());
    }

    @Test
    void testThatPaystackBeingUnreachableStillStartsTheAppAndCreatesNothing() {
        when(paystackClient.listSubaccounts()).thenThrow(new PaymentGatewayUnavailableException("Paystack service is currently down"));

        assertDoesNotThrow(() -> dataSeeder.run());

        assertNull(codeOf(DataSeeder.CORNER_TABLE_SLUG));
        assertNull(codeOf(DataSeeder.MAMA_PUT_SLUG));
        verify(paystackClient, never()).createSubaccount(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testThatARestaurantThatAlreadyHasASubaccountKeepsItAndIsNotAskedAboutAgain() {
        Tenant corner = tenantRepo.findBySlug(DataSeeder.CORNER_TABLE_SLUG).orElseThrow();
        corner.setPaystackSubaccountCode("ACCT_keep0001");
        tenantRepo.save(corner);
        when(paystackClient.listSubaccounts()).thenReturn(List.of(new SubaccountData("ACCT_different", CORNER)));

        dataSeeder.run();

        assertEquals("ACCT_keep0001", codeOf(DataSeeder.CORNER_TABLE_SLUG));
    }
}
