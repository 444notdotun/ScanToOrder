package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.respond.StaffResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkerServiceTest {
    @Autowired
    private WorkerService workerService;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tenantId;
    private String otherTenantId;

    @BeforeEach
    void setUp() {
        tenantId = tenants.mainId();
        otherTenantId = tenants.other().getTenantId();
    }

    private String handle() {
        return "h" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void testThatChefsAndWaitersCanBeCreatedWithAOneTimeTemporaryPassword() {
        String handle = handle();
        StaffResponse chef = workerService.createWorker(tenantId, "Chef One", "chef", handle);
        StaffResponse waiter = workerService.createWorker(tenantId, "Waiter One", "WAITER", handle());

        assertEquals("CHEF", chef.getRole());
        assertEquals("WAITER", waiter.getRole());
        assertEquals(TenantTestSupport.MAIN_SLUG + "." + handle, chef.getUsername());
        assertTrue(chef.getTemporaryPassword().length() >= 12);

        Worker saved = workerRepo.findByUsername(chef.getUsername()).orElseThrow();
        assertTrue(saved.isMustChangePassword());
        assertNotEquals(chef.getTemporaryPassword(), saved.getPassword());
        assertTrue(passwordEncoder.matches(chef.getTemporaryPassword(), saved.getPassword()));
        assertEquals(tenantId, saved.getTenant().getTenantId());

        assertNull(workerService.getAllWorkers(tenantId).stream()
                .filter(listed -> listed.getWorkerId().equals(chef.getWorkerId())).findFirst().orElseThrow().getTemporaryPassword());
    }

    @Test
    void testThatEachTemporaryPasswordIsDifferent() {
        assertNotEquals(workerService.createWorker(tenantId, "A", "chef", handle()).getTemporaryPassword(),
                workerService.createWorker(tenantId, "B", "chef", handle()).getTemporaryPassword());
    }

    @Test
    void testThatAManagerOrAPlatformAdminCannotBeCreated() {
        String handle = handle();
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker(tenantId, "Sneaky", "MANAGER", handle));
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker(tenantId, "Sneaky", "manager", handle));
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker(tenantId, "Sneaky", "PLATFORM_ADMIN", handle));
        assertFalse(workerRepo.existsByUsername(TenantTestSupport.MAIN_SLUG + "." + handle));
    }

    @Test
    void testThatAnUnknownRoleOrABadHandleIsRejected() {
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker(tenantId, "Nobody", "owner", handle()));
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker(tenantId, "Nobody", null, handle()));
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker(tenantId, "Nobody", "chef", "a b"));
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker(tenantId, "Nobody", "chef", "x"));
    }

    @Test
    void testThatAHandleCanBeUsedOnlyOncePerBusinessButAgainAtAnotherBusiness() {
        String handle = handle();
        workerService.createWorker(tenantId, "First", "waiter", handle);
        assertThrows(ConflictException.class, () -> workerService.createWorker(tenantId, "Second", "waiter", handle));
        assertEquals(TenantTestSupport.OTHER_SLUG + "." + handle,
                workerService.createWorker(otherTenantId, "Second", "waiter", handle).getUsername());
    }

    @Test
    void testThatRemovingAWorkerDeactivatesThemAndTheyLeaveTheList() {
        StaffResponse waiter = workerService.createWorker(tenantId, "Leaver", "waiter", handle());

        workerService.deleteWorker(tenantId, waiter.getWorkerId());

        Worker saved = workerRepo.findById(waiter.getWorkerId()).orElseThrow();
        assertFalse(saved.isActive());
        assertFalse(saved.isEnabled());
        assertTrue(workerService.getAllWorkers(tenantId).stream().noneMatch(listed -> listed.getWorkerId().equals(waiter.getWorkerId())));
        assertThrows(ResourceNotFoundException.class, () -> workerService.deleteWorker(tenantId, waiter.getWorkerId()));
    }

    @Test
    void testThatTheManagerCannotBeRemoved() {
        Worker manager = new Worker();
        manager.setTenant(tenants.main());
        manager.setUsername(TenantTestSupport.MAIN_SLUG + ".mgr" + handle());
        manager.setFullName("Manager");
        manager.setRole(WorkerRole.MANAGER);
        manager.setPassword("x");
        Worker saved = workerRepo.save(manager);
        assertThrows(ConflictException.class, () -> workerService.deleteWorker(tenantId, saved.getWorkerId()));
        assertTrue(workerRepo.findById(saved.getWorkerId()).orElseThrow().isActive());
    }
}
