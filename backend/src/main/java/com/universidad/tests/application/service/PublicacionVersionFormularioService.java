package com.universidad.tests.application.service;

import com.universidad.tests.domain.model.ConfiguracionSubprueba;
import com.universidad.tests.domain.model.EntradaClaveBFA;
import com.universidad.tests.domain.model.EstadoVersionClaveBFA;
import com.universidad.tests.domain.model.EstadoVersionFormulario;
import com.universidad.tests.domain.model.Pregunta;
import com.universidad.tests.domain.model.Subprueba;
import com.universidad.tests.domain.model.TipoRespuesta;
import com.universidad.tests.domain.model.VersionFormulario;
import com.universidad.tests.domain.port.AutorizacionContenidoPort;
import com.universidad.tests.domain.repository.*;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicacionVersionFormularioService {

    private final VersionFormularioRepository versionRepository;
    private final ConfiguracionSubpruebaRepository configuracionRepository;
    private final SubpruebaRepository subpruebaRepository;
    private final PreguntaRepository preguntaRepository;
    private final OpcionRespuestaRepository opcionRepository;
    private final VersionClaveBFARepository claveRepository;
    private final EntradaClaveBFARepository entradaClaveRepository;
    private final AutorizacionContenidoPort autorizacion;

    public PublicacionVersionFormularioService(
            VersionFormularioRepository versionRepository,
            ConfiguracionSubpruebaRepository configuracionRepository,
            SubpruebaRepository subpruebaRepository,
            PreguntaRepository preguntaRepository,
            OpcionRespuestaRepository opcionRepository,
            VersionClaveBFARepository claveRepository,
            EntradaClaveBFARepository entradaClaveRepository,
            AutorizacionContenidoPort autorizacion) {

        this.versionRepository = versionRepository;
        this.configuracionRepository = configuracionRepository;
        this.subpruebaRepository = subpruebaRepository;
        this.preguntaRepository = preguntaRepository;
        this.opcionRepository = opcionRepository;
        this.claveRepository = claveRepository;
        this.entradaClaveRepository = entradaClaveRepository;
        this.autorizacion = autorizacion;
    }

    @Transactional
    public VersionFormulario publicar(Long idVersionFormulario, String evidenciaValidacion) {

        validarId(idVersionFormulario);

        // Exigir permiso antes de publicar.
        autorizacion.exigirPermisoPublicar(idVersionFormulario);

        VersionFormulario version = buscarVersion(idVersionFormulario);

        if (version.getEstado() != EstadoVersionFormulario.BORRADOR) {

            throw new IllegalStateException("Solo se puede publicar una version en BORRADOR");
        }

        if (evidenciaValidacion == null || evidenciaValidacion.isBlank()) {

            throw new IllegalArgumentException("La evidencia de validacion es obligatoria");
        }

        // Comprobar que el formulario tenga contenido valido.
        validarContenido(version);

        // Registrar el cambio de estado.
        version.publicar(evidenciaValidacion);

        return versionRepository.guardar(version);
    }

    @Transactional
    public VersionFormulario retirar(Long idVersionFormulario) {

        validarId(idVersionFormulario);

        // Exigir permiso antes de retirar.
        autorizacion.exigirPermisoRetirar(idVersionFormulario);

        VersionFormulario version = buscarVersion(idVersionFormulario);

        if (version.getEstado() != EstadoVersionFormulario.PUBLICADA) {

            throw new IllegalStateException("Solo se puede retirar una version PUBLICADA");
        }

        version.retirar();

        return versionRepository.guardar(version);
    }

    private void validarContenido(VersionFormulario version) {
        Long idVersion = version.getIdVersionFormulario();
        List<ConfiguracionSubprueba> configuraciones =
                configuracionRepository.listarPorVersion(idVersion);
        if (configuraciones.isEmpty()) {
            throw new VersionIncompletaException("No se puede publicar una version sin subpruebas");
        }
        Set<Long> preguntasCerradas = new HashSet<>();
        for (ConfiguracionSubprueba configuracion : configuraciones) {
            Subprueba subprueba =
                    subpruebaRepository
                            .buscarPorId(configuracion.getIdSubprueba())
                            .orElseThrow(
                                    () ->
                                            new NoSuchElementException(
                                                    "No existe una subprueba de la configuracion"));

            if (!Objects.equals(subprueba.getIdTest(), version.getIdTest())) {
                throw new VersionIncompletaException("Una subprueba pertenece a otro test");
            }
            if (configuracion.getInstruccionesRef() == null
                    || configuracion.getInstruccionesRef().isBlank()) {
                throw new VersionIncompletaException("Faltan instrucciones de la subprueba");
            }
            if ("FV".equalsIgnoreCase(subprueba.getCodigo())
                    && (configuracion.getTipoRespuesta() != TipoRespuesta.TEXTO_LIBRE
                            || configuracion.getMaximoEntradas() == null)) {
                throw new VersionIncompletaException("La configuracion de FV esta incompleta");
            }
            List<Pregunta> preguntas =
                    preguntaRepository.listarPorConfiguracion(
                            configuracion.getIdConfiguracionSubprueba());
            if (preguntas.isEmpty()
                    && configuracion.getTipoRespuesta() == TipoRespuesta.SELECCION) {
                throw new VersionIncompletaException(
                        "La subprueba " + subprueba.getCodigo() + " no contiene preguntas");
            }
            if (configuracion.getTipoRespuesta() == TipoRespuesta.SELECCION) {
                for (Pregunta pregunta : preguntas) {
                    preguntasCerradas.add(pregunta.getIdPregunta());
                    int cantidad =
                            opcionRepository.listarPorPregunta(pregunta.getIdPregunta()).size();
                    if (cantidad == 0
                            || (configuracion.getOpcionesEsperadas() != null
                                    && cantidad != configuracion.getOpcionesEsperadas())) {
                        throw new VersionIncompletaException(
                                "La pregunta "
                                        + pregunta.getNumeroVisible()
                                        + " no tiene las opciones configuradas");
                    }
                }
            }
        }
        if (!preguntasCerradas.isEmpty()) {
            boolean claveCompleta =
                    claveRepository.listarPorVersionFormulario(idVersion).stream()
                            .filter(c -> c.getEstado() == EstadoVersionClaveBFA.APROBADA)
                            .anyMatch(
                                    c -> {
                                        List<EntradaClaveBFA> entradas =
                                                entradaClaveRepository.listarPorClave(
                                                        c.getIdClave());
                                        Set<Long> cubiertas = new HashSet<>();
                                        for (EntradaClaveBFA entrada : entradas) {
                                            cubiertas.add(entrada.getIdPregunta());
                                        }
                                        return entradas.size() == preguntasCerradas.size()
                                                && cubiertas.equals(preguntasCerradas);
                                    });
            if (!claveCompleta) {
                throw new VersionIncompletaException("Falta una clave BFA aprobada y completa");
            }
        }
    }

    private VersionFormulario buscarVersion(Long idVersionFormulario) {

        return versionRepository
                .buscarPorId(idVersionFormulario)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "No existe la version con ID: " + idVersionFormulario));
    }

    private void validarId(Long idVersionFormulario) {

        if (idVersionFormulario == null || idVersionFormulario <= 0) {

            throw new IllegalArgumentException("El ID de version debe ser positivo");
        }
    }

    public static class VersionIncompletaException extends IllegalStateException {
        public VersionIncompletaException(String mensaje) {
            super(mensaje);
        }
    }
}
