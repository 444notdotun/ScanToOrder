package com.scantoorder.scantoorder.config;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    @Autowired
    private UserDetailsService auth;
    @Autowired
    private JwtFilter  jwtFilter;
    @Autowired
    private ApiRateLimitFilter apiRateLimitFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(auth);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        return daoAuthenticationProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authentication) {
        return authentication.getAuthenticationManager();
    }
    @Bean
    public SecurityFilterChain securityConfiguration(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.HEAD, "/api/v1/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/t/*/tables/*/seatMap", "/api/v1/t/*/menu").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/signup/banks", "/api/v1/signup/name-available").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/signup", "/api/v1/signup/verify-account").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/seats/claim").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/t/*/e/*/tables/*/code").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/t/*/e/*/tables/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/seats/*/join").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/refunds/*/guest").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/refunds/*/guest/*").permitAll()

                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                    Object ended = req.getAttribute(JwtFilter.SESSION_ENDED_ATTRIBUTE);
                    String message = ended != null
                            ? (ended instanceof String text ? text : JwtFilter.SESSION_ENDED_MESSAGE) : "Unauthorized";
                    res.getWriter().write("{\"status\":\"ERROR\",\"message\":\"" + message + "\"}");
                }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Counted before anything else about the request is looked at.
                .addFilterBefore(apiRateLimitFilter, JwtFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public ModelMapper modelMapper(){
        return new ModelMapper();
    }
}
