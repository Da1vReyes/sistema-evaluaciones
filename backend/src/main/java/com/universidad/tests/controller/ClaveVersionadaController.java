package com.universidad.tests.controller;

import com.universidad.tests.application.service.CargarClaveVersionadaService;
import com.universidad.tests.application.service.CargarClaveVersionadaService.DatoClave;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClaveVersionadaController {
    private final CargarClaveVersionadaService servicio;

    public ClaveVersionadaController(CargarClaveVersionadaService servicio) {
        this.servicio = servicio;
    }

    @PutMapping("/api/v1/versiones/{versionFormularioId}/clave")
    public ClaveResponse cargar(
            @PathVariable Long versionFormularioId, @Valid @RequestBody ClaveRequest solicitud) {
        List<DatoClave> entradas =
                solicitud.entradas().stream()
                        .map(e -> new DatoClave(e.preguntaId(), e.opcion()))
                        .toList();
        return new ClaveResponse(servicio.cargar(versionFormularioId, entradas));
    }

    public record ClaveRequest(@NotEmpty List<@NotNull @Valid EntradaClaveRequest> entradas) {}

    public record EntradaClaveRequest(
            @NotNull @Positive Long preguntaId, @NotBlank String opcion) {}

    public record ClaveResponse(Long idClave) {}
}
