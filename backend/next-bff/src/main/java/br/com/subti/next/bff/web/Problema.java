package br.com.subti.next.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

/** Respostas de erro no formato Problem Details (RFC 9457), sem detalhes técnicos (Arquitetura 6.5). */
public final class Problema {

    private Problema() {
    }

    public static String json(int status, String codigo, String titulo, String detalhe, String instancia,
                              List<Map<String, String>> erros) {
        StringBuilder sb = new StringBuilder("{\"type\":\"urn:next:erro:").append(codigo).append("\",\"title\":\"")
                .append(esc(titulo)).append("\",\"status\":").append(status).append(",\"detail\":\"").append(esc(detalhe))
                .append("\",\"instance\":\"").append(esc(instancia)).append("\",\"correlationId\":\"")
                .append(esc(MDC.get("correlationId"))).append("\",\"errors\":[");
        for (int i = 0; i < erros.size(); i++) {
            Map<String, String> e = erros.get(i);
            sb.append(i == 0 ? "" : ",").append("{\"field\":\"").append(esc(e.get("field"))).append("\",\"message\":\"")
                    .append(esc(e.get("message"))).append("\"}");
        }
        return sb.append("]}").toString();
    }

    public static void escrever(HttpServletResponse res, HttpServletRequest req, int status, String codigo, String titulo,
                                String detalhe) throws IOException {
        res.setStatus(status);
        res.setContentType("application/problem+json;charset=UTF-8");
        res.getWriter().write(json(status, codigo, titulo, detalhe, req.getRequestURI(), List.of()));
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
