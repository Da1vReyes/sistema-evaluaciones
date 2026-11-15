package com.universidad.tests.application.service;

import com.universidad.tests.domain.model.EntradaClaveBFA;
import com.universidad.tests.domain.model.VersionClaveBFA;
import com.universidad.tests.domain.port.AutorizacionClaveBFAPort;
import com.universidad.tests.domain.repository.EntradaClaveBFARepository;
import com.universidad.tests.domain.repository.VersionClaveBFARepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidacionClaveBFAParaCalificacionService {

    private final VersionClaveBFARepository claveRepository;
    private final EntradaClaveBFARepository entradaRepository;
    private final ValidacionCoberturaClaveBFAService validadorCobertura;
    private final AutorizacionClaveBFAPort autorizacion;

    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    public ValidacionClaveBFAParaCalificacionService(
            VersionClaveBFARepository claveRepository,
            EntradaClaveBFARepository entradaRepository,
            ValidacionCoberturaClaveBFAService validadorCobertura,
            AutorizacionClaveBFAPort autorizacion) {

        this.claveRepository = claveRepository;
        this.entradaRepository = entradaRepository;
        this.validadorCobertura = validadorCobertura;
        this.autorizacion = autorizacion;
    }

    // =====================================================
    // 1. VALIDAR UNA CLAVE PARA NUEVAS CALIFICACIONES
    // =====================================================

    @Transactional(readOnly = true)
    public VersionClaveBFA obtenerClaveAprobada(Long idClave, Long idVersionFormulario) {

        // Validar los identificadores recibidos.
        validarId(idClave);
        validarId(idVersionFormulario);

        // Exigir autorizacion antes de consultar la clave.
        autorizacion.exigirPermisoCalificarConClave(idClave);

        // Comprobar que la clave exista.
        VersionClaveBFA clave =
                claveRepository
                        .buscarPorId(idClave)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "No existe la clave BFA indicada"));

        // Verificar que pertenezca al formulario solicitado.
        if (!idVersionFormulario.equals(clave.getIdVersionFormulario())) {

            throw new IllegalStateException(
                    "La clave BFA no pertenece " + "a la version de formulario indicada");
        }

        // Solo una clave APROBADA puede utilizarse
        // para generar nuevas calificaciones.
        clave.exigirAptaParaNuevaCalificacion();

        return clave;
    }

    // =====================================================
    // 2. OBTENER RESPUESTAS CORRECTAS PARA CALIFICACION
    // =====================================================

    @Transactional(readOnly = true)
    public List<EntradaClaveBFA> obtenerEntradasAprobadasParaCalificacion(
            Long idClave, Long idVersionFormulario) {

        // PASO 1:
        // Validar identificadores, autorizacion,
        // formulario y estado de aprobacion.
        obtenerClaveAprobada(idClave, idVersionFormulario);

        // PASO 2:
        // Comprobar que todas las preguntas de seleccion
        // tengan exactamente su entrada de correccion.
        //
        // El validador existente exige adicionalmente
        // permiso de consulta de claves.
        validadorCobertura.validarCoberturaSeleccion(idClave);

        // PASO 3:
        // Obtener las respuestas correctas registradas
        // para esta version especifica de la clave.
        List<EntradaClaveBFA> entradas = entradaRepository.listarPorClave(idClave);

        // PASO 4:
        // Devolver una copia no modificable.
        // El consumidor no podra agregar ni eliminar
        // entradas de la lista recibida.
        return List.copyOf(entradas);
    }

    // =====================================================
    // 3. VALIDAR IDENTIFICADORES
    // =====================================================

    private void validarId(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El identificador debe ser positivo");
        }
    }
}
