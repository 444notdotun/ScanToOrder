package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.service.Interface.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserDetailsService auth;

    @Autowired
    private SessionSeatRepo sessionSeatRepo;

    public static final String SESSION_ENDED_ATTRIBUTE = "plately.sessionEnded";
    public static final String SESSION_ENDED_MESSAGE = "Your session has ended";
    public static final String VISIT_ENDED_MESSAGE = "Your visit has ended.";
    public static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password";
    // Someone on the choose-a-password screen can also sign out.
    public static final String LOGOUT_PATH = "/api/v1/auth/logout";
    public static final String CHANGE_PASSWORD_MESSAGE = "Choose a new password before you carry on.";

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"" + message + "\"}");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        String jwt = null;

        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

//        // 2. Fall back to Authorization header
//        if (jwt == null) {
//            String authHeader = request.getHeader("Authorization");
//            if (authHeader != null && authHeader.startsWith("Bearer ")) {
//                jwt = authHeader.substring(7);
//            }
//        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.extractClaims(jwt);
            String type = claims.get("type", String.class);
            if (type != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (type.equals("CUSTOMER")) {
                    authenticateCustomer(claims, request);
                } else if ("WORKER".equals(type)) {
                    String username = claims.getSubject();
                    UserDetails userDetails = auth.loadUserByUsername(username);
                    if (jwtService.validateToken(jwt, userDetails) && userDetails.isEnabled() && !issuedBeforePasswordChange(claims, (Worker) userDetails)) {
                        Worker worker = (Worker) userDetails;
                        if (worker.isMustChangePassword() && !CHANGE_PASSWORD_PATH.equals(request.getRequestURI()) && !LOGOUT_PATH.equals(request.getRequestURI())) {
                            writeForbidden(response, CHANGE_PASSWORD_MESSAGE);
                            return;
                        }
                        // Role and tenant come from the account, so a changed account takes effect at once.
                        WorkerPrincipal principal = new WorkerPrincipal(
                                worker.getWorkerId(),
                                worker.getRole().name(),
                                worker.getTenant() == null ? null : worker.getTenant().getTenantId()
                        );
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(principal, null,
                                        userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT validation failed (e.g. expired): " + e.getMessage());
            // Do NOT throw or return 401 here; let Spring Security decide based on permitAll() rules
            // Clear the SecurityContext just to be safe
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    // A token issued before the last password change is a token for the old password. Compared in whole seconds, the precision a token keeps,
    // so the token handed out with the change itself is still good.
    private static boolean issuedBeforePasswordChange(Claims claims, Worker worker) {
        if (worker.getPasswordChangedAt() == null) {
            return false;
        }
        java.util.Date issued = claims.getIssuedAt();
        return issued == null || issued.toInstant().getEpochSecond() < worker.getPasswordChangedAt().getEpochSecond();
    }

    // A customer token is only as good as its seat: once the guest has left or the session
    // has closed, the token no longer signs anyone in.
    private void authenticateCustomer(Claims claims, HttpServletRequest request) {
        String sessionSeatId = claims.get("sessionSeatId", String.class);
        SessionSeat membership = sessionSeatId == null ? null : sessionSeatRepo.findById(sessionSeatId).orElse(null);
        boolean seated = membership != null
                && membership.getStatus() == SessionSeatStatus.OCCUPIED
                && membership.getSession().getSessionStatus() == DinningSessionStatus.ACTIVE;
        if (!seated) {
            // A table closed by staff says the visit ended; every other ending keeps the old words.
            boolean closedByStaff = membership != null && membership.getSession().getSessionStatus() == DinningSessionStatus.CLOSED
                    && membership.getSession().isClosedByStaff();
            request.setAttribute(SESSION_ENDED_ATTRIBUTE, closedByStaff ? VISIT_ENDED_MESSAGE : SESSION_ENDED_MESSAGE);
            return;
        }
        CustomerPrincipal principal = new CustomerPrincipal(
                membership.getSession().getSessionId(),
                membership.getSeat().getSeatId(),
                membership.getSeat().getTableId().getTableId(),
                membership.getId(),
                membership.getSession().getTenant().getTenantId()
        );
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
