package com.thehecklers.omakaseaircraftpos;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;

@ConfigurationPropertiesScan
@SpringBootApplication
public class OmakaseAircraftPosApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmakaseAircraftPosApplication.class, args);
    }

    @Bean
    @ConfigurationProperties(prefix = "airport")
    AirportProperties airport() {
        return new AirportProperties();
    }

    @Bean
    WebClient client() {
        return WebClient.create("http://localhost:7634/aircraft");
    }

    @Bean
    RSocketRequester requester(RSocketRequester.Builder builder) {
        return builder.tcp("localhost", 7635);
    }
}

@EnableWebFluxSecurity
class SecurityConfig {
    private PasswordEncoder pwEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Bean
    MapReactiveUserDetailsService authentication() {
        UserDetails alice = User.builder()
                .username("alice")
                .password(pwEncoder.encode("Alice#Password123"))
                .roles("USER", "ADMIN")
                .build();

        UserDetails bob = User.builder()
                .username("bob")
                .password(pwEncoder.encode("bob"))
                .roles("USER")
                .build();

        // For educational purposes only, don't log passwords!
        System.out.println("\n  >> Alice's password: " + alice.getPassword());
        System.out.println("  >>   Bob's password: " + bob.getPassword() + "\n");

        return new MapReactiveUserDetailsService(alice, bob);
    }

    @Bean
    SecurityWebFilterChain authorization(ServerHttpSecurity httpSecurity) {
        httpSecurity.authorizeExchange()
                .pathMatchers("/actuator/**", "/positions/**").hasRole("ADMIN")
                .anyExchange().authenticated()
                .and()
                .httpBasic()
                .and()
                .formLogin();

        return httpSecurity.build();
    }
}

@RestController
@RequestMapping("/positions")
@AllArgsConstructor
class PositionController {
    private final WebClient client;
    private final RSocketRequester requester;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<Aircraft> getPositions() {
        return client.get()
                .retrieve()
                .bodyToFlux(Aircraft.class)
                .delayElements(Duration.ofSeconds(1))
                .log();
    }

    @GetMapping(value = "/rsstream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<Aircraft> getRSStream() {
        return requester.route("acstream")
                .data(Instant.now())
                .retrieveFlux(Aircraft.class)
                .onBackpressureDrop()
                .take(10)
                .log();
    }
}

@RestController
@RequestMapping("/airport")
@AllArgsConstructor
class AirportController {
    private final AirportProperties airport;

    @GetMapping
    AirportProperties getAirport() {
        return airport;
    }
}

@RestController
@RequestMapping("/aircraft")
@AllArgsConstructor
class AircraftController {
    private final AircraftProperties acProps;

    @GetMapping("/reg")
    String getReg() {
        return acProps.getReg();
    }

    @GetMapping("/type")
    String getType() {
        return acProps.getType();
    }
}

@Data
class Aircraft {
    private String callsign, reg, flightno, type;
    private int altitude, heading, speed;
    private double lat, lon;
}

@Data
class AirportProperties {
    private String icaoCode, name;
}

@Data
@ConfigurationProperties(prefix = "aircraft")
class AircraftProperties {
    private String reg, type;
}