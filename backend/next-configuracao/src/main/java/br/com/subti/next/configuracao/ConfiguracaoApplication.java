package br.com.subti.next.configuracao;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * next-configuracao — Módulo de serviço Configuração do Next (N01 a N10).
 * Exposto apenas na rede interna; o único cliente é o next-bff (Arquitetura R2).
 */
@SpringBootApplication
@EnableScheduling
public class ConfiguracaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfiguracaoApplication.class, args);
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("Next - Módulo Configuração (API interna)").version("1.0.0")
                .description("API interna consumida pelo next-bff. Toda chamada exige o cabeçalho "
                        + "X-Next-Internal-Token assinado pelo BFF. CNPJ segue o padrão ^[A-Z0-9]{12}[0-9]{2}$."));
    }
}
