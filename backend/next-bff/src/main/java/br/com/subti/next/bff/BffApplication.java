package br.com.subti.next.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * next-bff — Backend for Frontend do Next. Único ponto de entrada externo (R2): sessão em cookie
 * HttpOnly, CSRF, RBAC por funcionalidade, trilha de acesso LGPD e roteamento para os módulos.
 */
@SpringBootApplication
@EnableAsync
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
