package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.DinningSessionStatus;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.SessionSeatStatus;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
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
                    if (jwtService.validateToken(jwt, userDetails)) {
                        WorkerPrincipal principal = new WorkerPrincipal(
                                claims.get("workerId", String.class),
                                claims.get("role", String.class)
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

    // A customer token is only as good as its seat: once the guest has left or the session
    // has closed, the token no longer signs anyone in.
    private void authenticateCustomer(Claims claims, HttpServletRequest request) {
        String sessionSeatId = claims.get("sessionSeatId", String.class);
        SessionSeat membership = sessionSeatId == null ? null : sessionSeatRepo.findById(sessionSeatId).orElse(null);
        boolean seated = membership != null
                && membership.getStatus() == SessionSeatStatus.OCCUPIED
                && membership.getSession().getSessionStatus() == DinningSessionStatus.ACTIVE;
        if (!seated) {
            request.setAttribute(SESSION_ENDED_ATTRIBUTE, Boolean.TRUE);
            return;
        }
        CustomerPrincipal principal = new CustomerPrincipal(
                membership.getSession().getSessionId(),
                membership.getSeat().getSeatId(),
                membership.getSeat().getTableId().getTableId(),
                membership.getId()
        );
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
