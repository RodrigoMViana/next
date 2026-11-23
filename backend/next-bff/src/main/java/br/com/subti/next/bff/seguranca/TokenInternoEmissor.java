package br.com.subti.next.bff.seguranca;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emite o token interno de curta duração que propaga a identidade do usuário aos módulos
 * (Arquitetura 6.2). Assinado com HS256; a chave vem do cofre de segredos e deve ser rotacionada.
 */
@Component
public class TokenInternoEmissor {

    private final MACSigner assinador;
    private final int validadeSegundos;

    public TokenInternoEmissor(@Value("${next.seguranca.token-interno.segredo}") String segredo,
                               @Value("${next.seguranca.token-interno.validade-segundos:60}") int validadeSegundos)
            throws JOSEException {
        if (segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("next.seguranca.token-interno.segredo deve ter ao menos 32 bytes.");
        }
        this.assinador = new MACSigner(segredo.getBytes(StandardCharsets.UTF_8));
        this.validadeSegundos = validadeSegundos;
    }

    public String emitir(UsuarioSessao u, String audiencia) {
        try {
            Date agora = new Date();
            JWTClaimsSet.Builder c = new JWTClaimsSet.Builder().subject(u.id()).issuer("next-bff").audience(audiencia)
                    .issueTime(agora).expirationTime(new Date(agora.getTime() + validadeSegundos * 1000L))
                    .jwtID(UUID.randomUUID().toString()).claim("nome", u.nome())
                    .claim("perfis", List.copyOf(u.perfis()));
            if (u.contratadaId() != null) {
                c.claim("contratadaId", u.contratadaId());
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), c.build());
            jwt.sign(assinador);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o token interno.", e);
        }
    }
}
