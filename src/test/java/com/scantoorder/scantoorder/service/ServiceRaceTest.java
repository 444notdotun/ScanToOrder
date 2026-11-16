package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.*;
import com.scantoorder.scantoorder.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two staff tapping the same thing at the same moment, for real: separate threads, separate transactions.
 * The first change wins and the second is told who got there first.
 */
@SpringBootTest
class ServiceRaceTest {
    private static final int ROUNDS = 6;

    @Autowired
    private OrderStepService steps;
    @Autowired
    private ServiceBoardService board;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private ServiceCallRepo callRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private ItemRepo itemRepo;

    private Tenant tenant;
    private Item plate;
    private final List<Worker> workers = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<ServiceCall> calls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tenant = tenants.main();
        plate = pay.item(tenant, "Race plate", 700, true);
    }

    @AfterEach
    void cleanUp() {
        for (ServiceCall call : calls) callRepo.deleteById(call.getServiceCallId());
        for (Order order : orders) {
            orderItemRepo.deleteAll(orderItemRepo.findByOrder_OrderId(order.getOrderId()));
            orderRepo.deleteById(order.getOrderId());
        }
        for (Worker worker : workers) workerRepo.deleteById(worker.getWorkerId());
    }

    private Worker worker(String name, WorkerRole role) {
        Worker worker = new Worker();
        worker.setTenant(tenant);
        worker.setFullName(name);
        worker.setUsername("race." + role.name().toLowerCase() + "." + System.nanoTime());
        worker.setPassword("x");
        worker.setRole(role);
        worker = workerRepo.save(worker);
        workers.add(worker);
        return worker;
    }

    private Order order(OrderStatus status) {
        Order order = pay.order(tenant, status, plate, 1);
        orders.add(order);
        return order;
    }

    // Both run together, released at the same instant. Returns each one's outcome: null for success, or the exception.
    private List<Throwable> race(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier go = new CyclicBarrier(2);
        try {
            List<Future<Throwable>> results = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                results.add(pool.submit(() -> {
                    go.await();
                    try {
                        task.call();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : results) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertOneWonOneWasTold(List<Throwable> outcomes, String wordsOfTheMessage) {
        assertEquals(1, outcomes.stream().filter(java.util.Objects::isNull).count(), "exactly one succeeds: " + outcomes);
        Throwable loser = outcomes.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertInstanceOf(ConflictException.class, loser);
        assertTrue(loser.getMessage().contains(wordsOfTheMessage), loser.getMessage());
    }

    @Test
    void testThatTwoChefsStartingTheSameOrderAtOnceLeaveOneWinnerWhoIsNamedToTheOther() throws Exception {
        Worker bisi = worker("Bisi Adebayo", WorkerRole.CHEF);
        Worker chidi = worker("Chidi Okoro", WorkerRole.CHEF);
        for (int round = 0; round < ROUNDS; round++) {
            Order order = order(OrderStatus.PAID);

            List<Throwable> outcomes = race(
                    () -> steps.start(tenant.getTenantId(), bisi.getWorkerId(), order.getOrderId()),
                    () -> steps.start(tenant.getTenantId(), chidi.getWorkerId(), order.getOrderId()));

            assertOneWonOneWasTold(outcomes, "has already started " + order.getOrderNumber());
            Order saved = orderRepo.findOrderByOrderId(order.getOrderId());
            assertEquals(OrderStatus.PREPARING, saved.getOrderStatus());
            String winner = saved.getPreparingBy();
            assertTrue(winner.equals("Chef Bisi") || winner.equals("Chef Chidi"));
            assertTrue(outcomes.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow().getMessage().startsWith(winner));
        }
    }

    @Test
    void testThatTwoWaitersDeliveringTheSameOrderAtOnceLeaveOneWinner() throws Exception {
        Worker emeka = worker("Emeka Eze", WorkerRole.WAITER);
        Worker ngozi = worker("Ngozi Bello", WorkerRole.WAITER);
        for (int round = 0; round < ROUNDS; round++) {
            Order order = order(OrderStatus.READY);

            List<Throwable> outcomes = race(
                    () -> steps.deliver(tenant.getTenantId(), emeka.getWorkerId(), order.getOrderId()),
                    () -> steps.deliver(tenant.getTenantId(), ngozi.getWorkerId(), order.getOrderId()));

            assertOneWonOneWasTold(outcomes, "has already delivered " + order.getOrderNumber());
            assertEquals(OrderStatus.DELIVERED, orderRepo.findOrderByOrderId(order.getOrderId()).getOrderStatus());
        }
    }

    @Test
    void testThatTwoWaitersTakingTheSameCallAtOnceLeaveOneWinnerWhoIsNamedToTheOther() throws Exception {
        Worker emeka = worker("Emeka Eze", WorkerRole.WAITER);
        Worker ngozi = worker("Ngozi Bello", WorkerRole.WAITER);
        for (int round = 0; round < ROUNDS; round++) {
            ServiceCall call = new ServiceCall();
            call.setTenant(tenant);
            call.setServiceDescription("Water");
            call = callRepo.save(call);
            calls.add(call);
            String id = call.getServiceCallId();

            List<Throwable> outcomes = race(
                    () -> board.take(tenant.getTenantId(), emeka.getWorkerId(), id),
                    () -> board.take(tenant.getTenantId(), ngozi.getWorkerId(), id));

            assertOneWonOneWasTold(outcomes, "has already taken");
            ServiceCall saved = callRepo.findById(id).orElseThrow();
            assertEquals(ServiceStatus.IN_PROGRESS, saved.getServiceStatus());
            String winner = saved.getTakenBy().getFullName().split(" ")[0];
            assertTrue(outcomes.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow().getMessage().startsWith(winner + " has already taken"));
        }
    }

    @Test
    void testThatTwoChefsMarkingTheSameOrderReadyAtOnceLeaveOneWinner() throws Exception {
        Worker bisi = worker("Bisi Adebayo", WorkerRole.CHEF);
        Worker chidi = worker("Chidi Okoro", WorkerRole.CHEF);
        for (int round = 0; round < ROUNDS; round++) {
            Order order = order(OrderStatus.PREPARING);

            List<Throwable> outcomes = race(
                    () -> steps.ready(tenant.getTenantId(), bisi.getWorkerId(), order.getOrderId()),
                    () -> steps.ready(tenant.getTenantId(), chidi.getWorkerId(), order.getOrderId()));

            assertOneWonOneWasTold(outcomes, "has already marked " + order.getOrderNumber() + " ready");
            assertEquals(OrderStatus.READY, orderRepo.findOrderByOrderId(order.getOrderId()).getOrderStatus());
        }
    }
}
