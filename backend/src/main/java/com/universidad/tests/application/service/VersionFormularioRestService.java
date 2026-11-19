package com.universidad.tests.application.service;

import com.universidad.tests.domain.model.VersionFormulario;
import org.springframework.stereotype.Service;

@Service
public class VersionFormularioRestService {
    private final VersionFormularioService versiones;
    private final PublicacionVersionFormularioService publicacion;

    public VersionFormularioRestService(
            VersionFormularioService versiones, PublicacionVersionFormularioService publicacion) {
        this.versiones = versiones;
        this.publicacion = publicacion;
    }

    public ResumenVersion crear(Long idTest, String codigo, String descripcion, String fuenteRef) {
        return resumen(
                versiones.crearBorradorDesdeUltimaPublicada(
                        idTest, codigo, descripcion, fuenteRef));
    }

    public ResumenVersion publicar(Long idVersion, String evidencia) {
        return resumen(publicacion.publicar(idVersion, evidencia));
    }

    private ResumenVersion resumen(VersionFormulario version) {
        return new ResumenVersion(
                version.getIdVersionFormulario(), version.getIdTest(), version.getEstado().name());
    }

    public record ResumenVersion(Long id, Long testId, String estado) {}
}
