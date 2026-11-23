package br.com.subti.next.configuracao;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Gera tokens internos como o BFF faria (somente para testes). */
public final class TokenTeste {

    public static final String SEGREDO = "dev-somente-local-troque-este-segredo-0123456789";

    private TokenTeste() {
    }

    public static String token(String usuarioId, String nome, List<String> perfis, UUID contratadaId) {
        return token(usuarioId, nome, perfis, contratadaId, SEGREDO, 60);
    }

    public static String token(String usuarioId, String nome, List<String> perfis, UUID contratadaId, String segredo,
                               int segundos) {
        try {
            JWTClaimsSet.Builder c = new JWTClaimsSet.Builder().subject(usuarioId).issuer("next-bff")
                    .audience("next-configuracao").issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + segundos * 1000L))
                    .claim("nome", nome).claim("perfis", perfis);
            if (contratadaId != null) {
                c.claim("contratadaId", contratadaId.toString());
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), c.build());
            jwt.sign(new MACSigner(segredo.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
