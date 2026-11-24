package com.universidad.tests.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

// Formato comun de error (Documento general 5.2.2)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String codigo,
        String mensaje,
        String ruta,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Detalle> detalles) {
    public ErrorResponse(
            Instant timestamp, int status, String codigo, String mensaje, String ruta) {
        this(timestamp, status, codigo, mensaje, ruta, null);
    }

    public record Detalle(String campo, String mensaje) {}
}
