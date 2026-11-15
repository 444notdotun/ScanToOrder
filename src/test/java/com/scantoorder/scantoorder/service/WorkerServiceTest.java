package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.service.Interface.WorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkerServiceTest {
    @Autowired
    private WorkerService workerService;
    @Autowired
    private WorkerRepo workerRepo;

    private String username() {
        return "staff-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void testThatChefsAndWaitersCanBeCreated() {
        Worker chef = workerService.createWorker("Chef One", "chef", username());
        Worker waiter = workerService.createWorker("Waiter One", "WAITER", username());
        assertEquals(WorkerRole.CHEF, chef.getRole());
        assertEquals(WorkerRole.WAITER, waiter.getRole());
    }

    @Test
    void testThatAManagerCannotBeCreated() {
        String username = username();
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker("Sneaky", "MANAGER", username));
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker("Sneaky", "manager", username));
        assertFalse(workerRepo.existsByUsername(username));
    }

    @Test
    void testThatAnUnknownRoleIsRejected() {
        String username = username();
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker("Nobody", "owner", username));
        assertThrows(InvalidRequestException.class, () -> workerService.createWorker("Nobody", null, username));
        assertFalse(workerRepo.existsByUsername(username));
    }
}
