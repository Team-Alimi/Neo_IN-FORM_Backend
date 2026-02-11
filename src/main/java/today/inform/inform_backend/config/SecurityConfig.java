package today.inform.inform_backend.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import today.inform.inform_backend.config.jwt.JwtAuthenticationFilter;



@Configuration

@EnableWebSecurity

@RequiredArgsConstructor

public class SecurityConfig {



    private final JwtAuthenticationFilter jwtAuthenticationFilter;



    @Bean

    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http

            .csrf(AbstractHttpConfigurer::disable)

            .formLogin(AbstractHttpConfigurer::disable)

            .httpBasic(AbstractHttpConfigurer::disable)

            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll() // 로그인 관련 API는 허용
                .requestMatchers("/api/v1/club_articles/**").permitAll() // 동아리 공지사항은 비로그인 허용
                .requestMatchers("/api/v1/users/**").hasRole("USER") // 사용자 관련 API는 인증 필요
                .requestMatchers("/api/v1/school_articles").hasRole("USER") // 목록 조회 허용
                .requestMatchers("/api/v1/school_articles/**").hasRole("USER") // 상세 조회 등 하위 경로 허용
                .anyRequest().authenticated()
            )

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        

        return http.build();

    }

}
