package com.universidad.tests.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.universidad.tests.application.service.CargarBaremoBFAService;
import com.universidad.tests.application.service.CargarClaveVersionadaService;
import com.universidad.tests.application.service.EdicionContenidoVersionadoService;
import com.universidad.tests.application.service.GestionClaveBFAService;
import com.universidad.tests.application.service.OpcionesVersionadasService;
import com.universidad.tests.application.service.VersionFormularioService;
import com.universidad.tests.domain.model.*;
import com.universidad.tests.domain.port.AutorizacionBaremoPort;
import com.universidad.tests.domain.port.AutorizacionClaveBFAPort;
import com.universidad.tests.domain.port.AutorizacionContenidoPort;
import com.universidad.tests.domain.repository.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiciosVersionadosTest {
    @Mock private VersionFormularioRepository versiones;
    @Mock private TestRepository tests;
    @Mock private ConfiguracionSubpruebaRepository configuraciones;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRespuestaRepository opciones;
    @Mock private VersionClaveBFARepository claves;
    @Mock private EntradaClaveBFARepository entradas;
    @Mock private BaremoBFARepository baremos;
    @Mock private AutorizacionContenidoPort autorizacionContenido;
    @Mock private AutorizacionClaveBFAPort autorizacionClave;
    @Mock private AutorizacionBaremoPort autorizacionBaremo;
    @Mock private EdicionContenidoVersionadoService edicion;
    @Mock private GestionClaveBFAService gestionClave;

    @Test
    void e25CopiaContenidoDeUltimaPublicadaSinModificarOrigen() {
        VersionFormulario origen = new VersionFormulario(10L, 1L, "v1", null, "DOC-1");
        origen.publicar("ACTA-1");
        VersionFormulario nuevo = new VersionFormulario(11L, 1L, "v2", null, "DOC-2");
        ConfiguracionSubprueba configuracion =
                new ConfiguracionSubprueba(
                        20L,
                        10L,
                        3L,
                        1,
                        300,
                        TipoRespuesta.SELECCION,
                        "Instrucciones",
                        null,
                        null,
                        2);
        ConfiguracionSubprueba copia =
                new ConfiguracionSubprueba(
                        21L,
                        11L,
                        3L,
                        1,
                        300,
                        TipoRespuesta.SELECCION,
                        "Instrucciones",
                        null,
                        null,
                        2);
        Pregunta pregunta = new Pregunta(30L, 20L, "1", 1, "Serie", null);
        Pregunta copiaPregunta = new Pregunta(31L, 21L, "1", 1, "Serie", null);
        OpcionRespuesta opcion = new OpcionRespuesta(40L, 30L, "A", 1, "Uno", null);
        when(tests.buscarPorId(1L))
                .thenReturn(Optional.of(mock(com.universidad.tests.domain.model.Test.class)));
        when(versiones.guardar(any(VersionFormulario.class))).thenReturn(nuevo);
        when(versiones.listarPorTest(1L)).thenReturn(List.of(origen, nuevo));
        when(configuraciones.listarPorVersion(10L)).thenReturn(List.of(configuracion));
        when(edicion.guardarConfiguracion(any())).thenReturn(copia);
        when(preguntas.listarPorConfiguracion(20L)).thenReturn(List.of(pregunta));
        when(edicion.guardarPregunta(any())).thenReturn(copiaPregunta);
        when(opciones.listarPorPregunta(30L)).thenReturn(List.of(opcion));
        VersionFormularioService servicio =
                new VersionFormularioService(
                        versiones,
                        tests,
                        autorizacionContenido,
                        configuraciones,
                        preguntas,
                        opciones,
                        edicion);

        VersionFormulario resultado =
                servicio.crearBorradorDesdeUltimaPublicada(1L, "v2", null, "DOC-2");

        assertEquals(EstadoVersionFormulario.BORRADOR, resultado.getEstado());
        assertEquals(EstadoVersionFormulario.PUBLICADA, origen.getEstado());
        ArgumentCaptor<OpcionRespuesta> captor = ArgumentCaptor.forClass(OpcionRespuesta.class);
        verify(edicion).guardarOpcion(captor.capture());
        assertEquals(31L, captor.getValue().getIdPregunta());
        assertEquals("A", captor.getValue().getCodigo());
    }

    @Test
    void e28NoEditaOpcionesDeVersionPublicada() {
        VersionFormulario publicada = new VersionFormulario(11L, 1L, "v2", null, "DOC");
        publicada.publicar("ACTA");
        when(versiones.buscarPorId(11L)).thenReturn(Optional.of(publicada));
        OpcionesVersionadasService servicio =
                new OpcionesVersionadasService(
                        versiones,
                        configuraciones,
                        preguntas,
                        opciones,
                        autorizacionContenido,
                        edicion);

        assertThrows(
                IllegalStateException.class,
                () ->
                        servicio.guardar(
                                11L,
                                120L,
                                List.of(
                                        new OpcionesVersionadasService.DatosOpcion(
                                                "A", 1, "Uno", null))));
        verifyNoInteractions(preguntas, opciones, edicion);
    }

    @Test
    void e29RelacionaPreguntaConOpcionYClaveBfa() {
        VersionFormulario version = new VersionFormulario(11L, 1L, "v2", null, "DOC");
        ConfiguracionSubprueba configuracion =
                new ConfiguracionSubprueba(
                        20L,
                        11L,
                        3L,
                        1,
                        300,
                        TipoRespuesta.SELECCION,
                        "Instrucciones",
                        null,
                        null,
                        2);
        Pregunta pregunta = new Pregunta(120L, 20L, "1", 1, "Serie", null);
        OpcionRespuesta opcion = new OpcionRespuesta(7L, 120L, "A", 1, "Uno", null);
        VersionClaveBFA clave = new VersionClaveBFA(4L, 11L, "v2", "DOC");
        when(versiones.buscarPorId(11L)).thenReturn(Optional.of(version));
        when(configuraciones.listarPorVersion(11L)).thenReturn(List.of(configuracion));
        when(preguntas.listarPorConfiguracion(20L)).thenReturn(List.of(pregunta));
        when(opciones.buscarPorCodigo(120L, "A")).thenReturn(Optional.of(opcion));
        when(gestionClave.crearClave(11L, "v2", "DOC")).thenReturn(clave);
        CargarClaveVersionadaService servicio =
                new CargarClaveVersionadaService(
                        versiones,
                        configuraciones,
                        preguntas,
                        opciones,
                        claves,
                        entradas,
                        gestionClave,
                        autorizacionContenido,
                        autorizacionClave);

        assertEquals(
                4L,
                servicio.cargar(
                        11L, List.of(new CargarClaveVersionadaService.DatoClave(120L, "A"))));
        ArgumentCaptor<List<EntradaClaveBFA>> captor = ArgumentCaptor.forClass(List.class);
        verify(gestionClave).registrarEntradas(eq(4L), captor.capture());
        assertEquals(7L, captor.getValue().get(0).getIdOpcionCorrecta());
    }

    @Test
    void e30RechazaRangosSuperpuestosAntesDeGuardar() {
        CargarBaremoBFAService servicio = new CargarBaremoBFAService(baremos, autorizacionBaremo);
        BaremoBFA baremo =
                new BaremoBFA(
                        null,
                        "BFA",
                        "v1",
                        1992,
                        null,
                        null,
                        List.of(
                                new RangoBaremoBFA(
                                        "VOC1", 5, 1, 6, EstadoCeldaBaremo.RANGO, null, null),
                                new RangoBaremoBFA(
                                        "VOC1", 10, 6, 12, EstadoCeldaBaremo.RANGO, null, null)));

        assertThrows(
                CargarBaremoBFAService.BaremoInvalidoException.class,
                () -> servicio.cargar(baremo));
        verify(baremos, never()).guardar(any());
    }
}
