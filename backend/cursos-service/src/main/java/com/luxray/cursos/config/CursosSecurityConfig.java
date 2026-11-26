package com.luxray.cursos.config;

import com.luxray.common.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class CursosSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           UsuarioActualFilter usuarioActualFilter) throws Exception {
        // CORS lo gestiona exclusivamente el API Gateway. No configuramos CORS aquí
        // para evitar headers duplicados cuando el request pasa por el gateway.
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/cursos").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/cursos/*").permitAll()
                        .requestMatchers("/cursos/ws", "/cursos/ws/**", "/ws", "/ws/**").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(usuarioActualFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
