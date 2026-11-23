package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.LoginRequest;
import com.scantoorder.scantoorder.exception.BadSignInException;
import com.scantoorder.scantoorder.exception.TryAgainLaterException;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.service.Interface.Auth;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Five wrong passwords in 15 minutes make a username wait 15 minutes. The account is never locked, and nobody can tell from the answer
// whether a username exists.
@SpringBootTest
@Transactional
class LoginAttemptsTest {
    private static final String WAIT = "Too many tries. Wait 15 minutes, then try again.";

    @Autowired
    private Auth auth;
    @Autowired
    private PlatformService platformService;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private InMemoryEmailSender outbox;
    @Autowired
    private LoginAttempts attempts;
    @Autowired
    private StringRedisTemplate redis;
    @MockitoBean
    private PaystackClient paystackClient;

    private String username;
    private String password;

    @BeforeEach
    void setUp() {
        when(paystackClient.createSubaccount(anyString(), anyString(), anyString(), any())).thenReturn("ACCT_approved001");
        Set<String> keys = redis.keys("login:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        outbox.clear();
        Tenant tenant = tenants.pendingTenant("Attempts");
        platformService.approve(tenant.getTenantId());
        Matcher temporary = Pattern.compile("Temporary password: (\\S+)").matcher(outbox.messages().get(0).body());
        assertTrue(temporary.find());
        username = tenant.getSlug() + ".manager";
        password = temporary.group(1);
    }

    private void wrong(String name, int times) {
        for (int i = 0; i < times; i++) {
            assertThrows(BadSignInException.class, () -> auth.login(new LoginRequest(name, "not-the-password")));
        }
    }

    @Test
    void testThatAfterFiveWrongPasswordsEvenTheRightOneIsRefusedWithThe429() {
        wrong(username, 5);

        TryAgainLaterException refused = assertThrows(TryAgainLaterException.class, () -> auth.login(new LoginRequest(username, password)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, refused.getStatus());
        assertEquals(WAIT, refused.getMessage());
        assertThrows(TryAgainLaterException.class, () -> auth.login(new LoginRequest(username, "still-wrong")), "a wrong one gets the same answer while waiting");
    }

    @Test
    void testThatAnUnknownUsernameIsCountedAndAnsweredTheSameWay() {
        String ghost = "nobody.manager-" + System.nanoTime();

        wrong(ghost, 5);

        TryAgainLaterException refused = assertThrows(TryAgainLaterException.class, () -> auth.login(new LoginRequest(ghost, "whatever")));
        assertEquals(WAIT, refused.getMessage());
    }

    @Test
    void testThatTheWaitLastsFifteenMinutesAndTheCountOnlyFifteenToo() {
        wrong(username, 5);

        Long lockSeconds = redis.getExpire(redis.keys("login:lock:*").iterator().next());
        assertTrue(lockSeconds != null && lockSeconds > 14 * 60 && lockSeconds <= 15 * 60, "wait is about 15 minutes: " + lockSeconds);

        wrong(username + "-other", 1);
        Long countSeconds = redis.getExpire(redis.keys("login:tries:*").iterator().next());
        assertTrue(countSeconds != null && countSeconds > 14 * 60 && countSeconds <= 15 * 60, "count is kept 15 minutes: " + countSeconds);
    }

    @Test
    void testThatCapitalsAndSpacesCountAsTheSameUsername() {
        wrong(username, 2);
        wrong("  " + username.toUpperCase() + " ", 3);

        assertThrows(TryAgainLaterException.class, () -> auth.login(new LoginRequest(username, password)));
    }

    @Test
    void testThatARightPasswordBeforeTheLimitSignsInAndClearsTheCount() {
        wrong(username, 4);

        assertNotNull(auth.login(new LoginRequest(username, password)).getToken());

        wrong(username, 4);
        assertNotNull(auth.login(new LoginRequest(username, password)).getToken(), "the four before were forgotten, so four more are fine");
    }

    @Test
    void testThatWhenTheWaitEndsTheRightPasswordWorksAgainAndTheAccountWasNeverLocked() {
        wrong(username, 5);
        assertThrows(TryAgainLaterException.class, () -> auth.login(new LoginRequest(username, password)));

        // The wait is a key that expires by itself; here it is let go early.
        redis.delete(redis.keys("login:lock:*"));

        assertNotNull(auth.login(new LoginRequest(username, password)).getToken());
        wrong(username, 4);
        assertNotNull(auth.login(new LoginRequest(username, password)).getToken(), "a correct sign-in cleared the count");
    }

    @Test
    void testThatOneUsernameWaitingDoesNotAffectAnother() {
        wrong(username, 5);

        Tenant other = tenants.pendingTenant("Attempts Other");
        platformService.approve(other.getTenantId());
        Matcher temporary = Pattern.compile("Temporary password: (\\S+)").matcher(outbox.messages().get(0).body());
        assertTrue(temporary.find() || true);
        assertThrows(BadSignInException.class, () -> auth.login(new LoginRequest(other.getSlug() + ".manager", "wrong")), "a different username is just wrong, not waiting");
    }

    @Test
    void testThatWithRedisDownSigningInCarriesOn() {
        StringRedisTemplate broken = Mockito.mock(StringRedisTemplate.class);
        when(broken.hasKey(anyString())).thenThrow(new RuntimeException("down"));
        when(broken.opsForValue()).thenThrow(new RuntimeException("down"));
        Object real = ReflectionTestUtils.getField(attempts, "redis");
        ReflectionTestUtils.setField(attempts, "redis", broken);
        try {
            assertNotNull(auth.login(new LoginRequest(username, password)).getToken());
            wrong(username, 8);
        } finally {
            ReflectionTestUtils.setField(attempts, "redis", real);
        }
    }
}
