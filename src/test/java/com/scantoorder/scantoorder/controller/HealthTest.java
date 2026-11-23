package com.scantoorder.scantoorder.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

// The health check for an outside scheduler: both work, "ok"; either does not, "unavailable" and nothing else.
@SpringBootTest
class HealthTest {

    @Autowired
    private WebApplicationContext wac;
    @Autowired
    private Health health;

    private MockMvc mvc;
    private Object realDatabase;
    private Object realRedis;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
        realDatabase = ReflectionTestUtils.getField(health, "database");
        realRedis = ReflectionTestUtils.getField(health, "redis");
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(health, "database", realDatabase);
        ReflectionTestUtils.setField(health, "redis", realRedis);
    }

    @Test
    void testThatBothWorkingAnswersOk() throws Exception {
        MvcResult answer = mvc.perform(get("/api/v1/health")).andReturn();

        assertEquals(200, answer.getResponse().getStatus());
        assertEquals("ok", answer.getResponse().getContentAsString());
    }

    @Test
    void testThatHeadIsAnsweredLikeGetWithNoBody() throws Exception {
        MvcResult answer = mvc.perform(head("/api/v1/health")).andReturn();

        assertEquals(200, answer.getResponse().getStatus());
        assertEquals("", answer.getResponse().getContentAsString());
    }

    @Test
    void testThatADownDatabaseAnswers503WithNoDetail() throws Exception {
        JdbcTemplate broken = Mockito.mock(JdbcTemplate.class);
        when(broken.queryForObject(anyString(), eq(Integer.class))).thenThrow(new RuntimeException("connection to db.internal:5432 refused, password=secret"));
        ReflectionTestUtils.setField(health, "database", broken);

        MvcResult answer = mvc.perform(get("/api/v1/health")).andReturn();

        assertEquals(503, answer.getResponse().getStatus());
        assertEquals("unavailable", answer.getResponse().getContentAsString());
        assertEquals(503, mvc.perform(head("/api/v1/health")).andReturn().getResponse().getStatus());
    }

    @Test
    void testThatADownRedisAnswers503WithNoDetail() throws Exception {
        StringRedisTemplate broken = Mockito.mock(StringRedisTemplate.class);
        when(broken.execute(Mockito.<org.springframework.data.redis.core.RedisCallback<String>>any())).thenThrow(new RuntimeException("redis.internal:6379 refused"));
        ReflectionTestUtils.setField(health, "redis", broken);

        MvcResult answer = mvc.perform(get("/api/v1/health")).andReturn();

        assertEquals(503, answer.getResponse().getStatus());
        assertEquals("unavailable", answer.getResponse().getContentAsString());
    }

    @Test
    void testThatAnswerThatTakesTooLongCountsAsDownAfterTwoSeconds() throws Exception {
        JdbcTemplate slow = Mockito.mock(JdbcTemplate.class);
        when(slow.queryForObject(anyString(), eq(Integer.class))).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return 1;
        });
        ReflectionTestUtils.setField(health, "database", slow);

        long started = System.currentTimeMillis();
        MvcResult answer = mvc.perform(get("/api/v1/health")).andReturn();
        long took = System.currentTimeMillis() - started;

        assertEquals(503, answer.getResponse().getStatus());
        assertEquals("unavailable", answer.getResponse().getContentAsString());
        assertTrue(took < 4_000, "gave up after about two seconds, not five: " + took);
    }
}
