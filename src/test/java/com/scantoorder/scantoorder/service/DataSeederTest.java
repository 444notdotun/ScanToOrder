package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.DataSeeder;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    @Value("${app.seed.manager-password}")
    private String managerPassword;

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
}
