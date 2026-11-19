package com.universidad.tests.application.service;

import com.universidad.tests.domain.model.ConfiguracionSubprueba;
import com.universidad.tests.domain.model.EntradaClaveBFA;
import com.universidad.tests.domain.model.EstadoVersionFormulario;
import com.universidad.tests.domain.model.OpcionRespuesta;
import com.universidad.tests.domain.model.Pregunta;
import com.universidad.tests.domain.model.TipoRespuesta;
import com.universidad.tests.domain.model.VersionClaveBFA;
import com.universidad.tests.domain.model.VersionFormulario;
import com.universidad.tests.domain.port.AutorizacionClaveBFAPort;
import com.universidad.tests.domain.port.AutorizacionContenidoPort;
import com.universidad.tests.domain.repository.ConfiguracionSubpruebaRepository;
import com.universidad.tests.domain.repository.EntradaClaveBFARepository;
import com.universidad.tests.domain.repository.OpcionRespuestaRepository;
import com.universidad.tests.domain.repository.PreguntaRepository;
import com.universidad.tests.domain.repository.VersionClaveBFARepository;
import com.universidad.tests.domain.repository.VersionFormularioRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CargarClaveVersionadaService {
    private final VersionFormularioRepository versiones;
    private final ConfiguracionSubpruebaRepository configuraciones;
    private final PreguntaRepository preguntas;
    private final OpcionRespuestaRepository opciones;
    private final VersionClaveBFARepository claves;
    private final EntradaClaveBFARepository entradas;
    private final GestionClaveBFAService gestion;
    private final AutorizacionContenidoPort autorizacionContenido;
    private final AutorizacionClaveBFAPort autorizacionClave;

    public CargarClaveVersionadaService(
            VersionFormularioRepository versiones,
            ConfiguracionSubpruebaRepository configuraciones,
            PreguntaRepository preguntas,
            OpcionRespuestaRepository opciones,
            VersionClaveBFARepository claves,
            EntradaClaveBFARepository entradas,
            GestionClaveBFAService gestion,
            AutorizacionContenidoPort autorizacionContenido,
            AutorizacionClaveBFAPort autorizacionClave) {
        this.versiones = versiones;
        this.configuraciones = configuraciones;
        this.preguntas = preguntas;
        this.opciones = opciones;
        this.claves = claves;
        this.entradas = entradas;
        this.gestion = gestion;
        this.autorizacionContenido = autorizacionContenido;
        this.autorizacionClave = autorizacionClave;
    }

    @Transactional
    public Long cargar(Long idVersion, List<DatoClave> datos) {
        if (idVersion == null || idVersion <= 0 || datos == null || datos.isEmpty()) {
            throw new IllegalArgumentException("La version y las entradas son obligatorias");
        }
        autorizacionContenido.exigirPermisoEditarContenido(idVersion);
        VersionFormulario version =
                versiones
                        .buscarPorId(idVersion)
                        .orElseThrow(() -> new NoSuchElementException("Version no encontrada"));
        if (version.getEstado() != EstadoVersionFormulario.BORRADOR) {
            throw new IllegalStateException("VERSION_NO_EDITABLE");
        }
        Set<Long> esperadas = new HashSet<>();
        for (ConfiguracionSubprueba configuracion : configuraciones.listarPorVersion(idVersion)) {
            if (configuracion.getTipoRespuesta() == TipoRespuesta.SELECCION) {
                for (Pregunta pregunta :
                        preguntas.listarPorConfiguracion(
                                configuracion.getIdConfiguracionSubprueba())) {
                    esperadas.add(pregunta.getIdPregunta());
                }
            }
        }
        Set<Long> recibidas = new HashSet<>();
        List<DatoPersistido> nuevas = new ArrayList<>();
        for (DatoClave dato : datos) {
            if (dato == null
                    || dato.preguntaId() == null
                    || dato.preguntaId() <= 0
                    || dato.opcion() == null
                    || dato.opcion().isBlank()
                    || !recibidas.add(dato.preguntaId())
                    || !esperadas.contains(dato.preguntaId())) {
                throw new ClaveInvalidaException();
            }
            OpcionRespuesta opcion =
                    opciones.buscarPorCodigo(dato.preguntaId(), dato.opcion())
                            .orElseThrow(ClaveInvalidaException::new);
            if (!Objects.equals(opcion.getIdPregunta(), dato.preguntaId())) {
                throw new ClaveInvalidaException();
            }
            nuevas.add(new DatoPersistido(dato.preguntaId(), opcion.getIdOpcion()));
        }
        if (!recibidas.equals(esperadas)) {
            throw new ClaveInvalidaException();
        }
        VersionClaveBFA clave =
                claves.buscarPorFormularioYVersion(idVersion, version.getCodigoVersion())
                        .orElse(null);
        if (clave == null) {
            clave =
                    gestion.crearClave(
                            idVersion, version.getCodigoVersion(), version.getFuenteRef());
        } else {
            if (!clave.esEditable()) {
                throw new IllegalStateException("La clave aprobada no se puede reemplazar");
            }
            autorizacionClave.exigirPermisoAgregarEntrada(clave.getIdClave());
            entradas.eliminarEntradasBorrador(clave.getIdClave());
        }
        List<EntradaClaveBFA> asociadas = new ArrayList<>();
        for (DatoPersistido entrada : nuevas) {
            asociadas.add(
                    new EntradaClaveBFA(
                            null, clave.getIdClave(), entrada.preguntaId(), entrada.opcionId()));
        }
        gestion.registrarEntradas(clave.getIdClave(), asociadas);
        return clave.getIdClave();
    }

    public record DatoClave(Long preguntaId, String opcion) {}

    private record DatoPersistido(Long preguntaId, Long opcionId) {}

    public static class ClaveInvalidaException extends RuntimeException {
        public ClaveInvalidaException() {
            super("CLAVE_INVALIDA");
        }
    }
}
