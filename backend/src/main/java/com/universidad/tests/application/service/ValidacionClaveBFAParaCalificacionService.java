package com.universidad.tests.application.service;

import com.universidad.tests.domain.model.VersionClaveBFA;
import com.universidad.tests.domain.port.AutorizacionClaveBFAPort;
import com.universidad.tests.domain.repository.VersionClaveBFARepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidacionClaveBFAParaCalificacionService {

    private final VersionClaveBFARepository claveRepository;
    private final AutorizacionClaveBFAPort autorizacion;

    public ValidacionClaveBFAParaCalificacionService(
            VersionClaveBFARepository claveRepository, AutorizacionClaveBFAPort autorizacion) {

        this.claveRepository = claveRepository;
        this.autorizacion = autorizacion;
    }

    // =====================================================
    // VALIDAR UNA CLAVE PARA NUEVAS CALIFICACIONES
    // =====================================================

    @Transactional(readOnly = true)
    public VersionClaveBFA obtenerClaveAprobada(Long idClave, Long idVersionFormulario) {

        // 1. Validar identificadores.
        validarId(idClave);
        validarId(idVersionFormulario);

        // 2. Exigir autorizacion antes de consultar la clave.
        autorizacion.exigirPermisoCalificarConClave(idClave);

        // 3. Comprobar que la clave exista.
        VersionClaveBFA clave =
                claveRepository
                        .buscarPorId(idClave)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No existe la clave BFA indicada"));

        // 4. Verificar que corresponda al formulario.
        if (!idVersionFormulario.equals(clave.getIdVersionFormulario())) {

            throw new IllegalStateException(
                    "La clave BFA no pertenece " + "a la version de formulario indicada");
        }

        // 5. Permitir solamente claves APROBADAS.
        clave.exigirAptaParaNuevaCalificacion();

        // 6. Devolver la clave validada.
        return clave;
    }

    // =====================================================
    // VALIDAR IDENTIFICADORES
    // =====================================================

    private void validarId(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El identificador debe ser positivo");
        }
    }
}
