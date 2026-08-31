package com.scantoorder.scantoorder;

import com.scantoorder.scantoorder.config.RedisConfiguration;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.data.model.Category;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.repository.CategoryRepository;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ScanToOrderApplication implements CommandLineRunner {
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ItemRepo itemRepo;

    public static void main(String[] args) {
        SpringApplication.run(ScanToOrderApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if(workerRepo.findByUsername("manager").isEmpty()){
            Worker manager = new Worker();
            manager.setPassword(passwordEncoder.encode("password11"));
            manager.setRole(WorkerRole.MANAGER);
            manager.setUsername("manager");
            manager.setFullName("adewole dotun");
            workerRepo.save(manager);
            redisTemplate.delete("menu:active");
        }
//
//        if (categoryRepository.count() == 0) {
//            Category food = new Category();
//            food.setCategoryName("Food");
//            categoryRepository.save(food);
//
//            Category drinks = new Category();
//            drinks.setCategoryName("Drinks");
//            categoryRepository.save(drinks);
//
//            Item burger = new Item();
//            burger.setItemName("Classic Burger");
//            burger.setItemDescription("A delicious classic burger with cheese");
//            burger.setItemPrice(new BigDecimal("9.99"));
//            burger.setCategoryId(food);
//            itemRepo.save(burger);
//
//            Item pizza = new Item();
//            pizza.setItemName("Pepperoni Pizza");
//            pizza.setItemDescription("Large pepperoni pizza");
//            pizza.setItemPrice(new BigDecimal("14.99"));
//            pizza.setCategoryId(food);
//            itemRepo.save(pizza);
//
//            Item coke = new Item();
//            coke.setItemName("Coca Cola");
//            coke.setItemDescription("Chilled Coca Cola Can");
//            coke.setItemPrice(new BigDecimal("1.99"));
//            coke.setCategoryId(drinks);
//            itemRepo.save(coke);
//        }
    }
}
