package com.universidad.tests;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.universidad.tests.application.service.CargarBaremoBFAService;
import com.universidad.tests.application.service.CargarBaremoBFAService.BaremoInvalidoException;
import com.universidad.tests.application.service.CargarClaveVersionadaService;
import com.universidad.tests.application.service.CargarClaveVersionadaService.ClaveInvalidaException;
import com.universidad.tests.application.service.ConfiguracionVersionadaService;
import com.universidad.tests.application.service.OpcionesVersionadasService;
import com.universidad.tests.application.service.OpcionesVersionadasService.DatosOpcion;
import com.universidad.tests.application.service.PublicacionVersionFormularioService.VersionIncompletaException;
import com.universidad.tests.application.service.VersionFormularioRestService;
import com.universidad.tests.application.service.VersionFormularioRestService.ResumenVersion;
import com.universidad.tests.controller.BaremoBFAController;
import com.universidad.tests.controller.ClaveVersionadaController;
import com.universidad.tests.controller.ConfiguracionVersionadaController;
import com.universidad.tests.controller.OpcionesVersionadasController;
import com.universidad.tests.controller.VersionFormularioController;
import com.universidad.tests.controller.VersionadoErrorHandler;
import com.universidad.tests.domain.exception.NoAutenticadoException;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    VersionFormularioController.class,
    ConfiguracionVersionadaController.class,
    OpcionesVersionadasController.class,
    ClaveVersionadaController.class,
    BaremoBFAController.class
})
@Import(VersionadoErrorHandler.class)
class VersionadoRestControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private VersionFormularioRestService versiones;
    @MockitoBean private ConfiguracionVersionadaService configuraciones;
    @MockitoBean private OpcionesVersionadasService opciones;
    @MockitoBean private CargarClaveVersionadaService claves;
    @MockitoBean private CargarBaremoBFAService baremos;

    private static final String CREAR = "/api/v1/tests/1/versiones";
    private static final String CREAR_BODY = "{\"codigoVersion\":\"v2\",\"fuenteRef\":\"DOC-BFA\"}";
    private static final String CONFIG = "/api/v1/versiones/11/configuraciones/R1";
    private static final String CONFIG_BODY =
            "{\"orden\":1,\"tipoRespuesta\":\"SELECCION\",\"opciones\":2,"
                    + "\"tienePractica\":false,\"instrucciones\":\"Lea las instrucciones\"}";
    private static final String OPCIONES = "/api/v1/versiones/11/preguntas/120/opciones";
    private static final String OPCIONES_BODY =
            "{\"opciones\":[{\"letra\":\"A\",\"orden\":1,\"texto\":\"Uno\"}]}";
    private static final String CLAVE = "/api/v1/versiones/11/clave";
    private static final String CLAVE_BODY =
            "{\"entradas\":[{\"preguntaId\":120,\"opcion\":\"A\"}]}";
    private static final String BAREMO = "/api/v1/baremos";
    private static final String BAREMO_BODY =
            "{\"nombre\":\"BFA\",\"version\":\"v1\",\"anioNorma\":1992,"
                    + "\"rangos\":[{\"idFactor\":\"VOC1\",\"percentil\":5,"
                    + "\"limiteInferior\":1,\"limiteSuperior\":5,\"estadoCelda\":\"RANGO\"}]}";
    private static final String PUBLICAR = "/api/v1/versiones/11/publicar";
    private static final String PUBLICAR_BODY = "{\"evidenciaValidacion\":\"ACTA-1\"}";

    @Test
    void e25CreaBorrador() throws Exception {
        when(versiones.crear(1L, "v2", null, "DOC-BFA"))
                .thenReturn(new ResumenVersion(11L, 1L, "BORRADOR"));
        mvc.perform(post(CREAR).contentType(MediaType.APPLICATION_JSON).content(CREAR_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(header().string("Location", "/api/v1/versiones/11"));
    }

    @Test
    void e25Distingue400401403Y404() throws Exception {
        mvc.perform(post(CREAR).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(versiones.crear(anyLong(), anyString(), any(), anyString()))
                .thenThrow(new NoAutenticadoException())
                .thenThrow(new SecurityException("sin permiso"))
                .thenThrow(new NoSuchElementException("test ausente"));
        for (int esperado : new int[] {401, 403, 404}) {
            mvc.perform(post(CREAR).contentType(MediaType.APPLICATION_JSON).content(CREAR_BODY))
                    .andExpect(status().is(esperado));
        }
    }

    @Test
    void e26GuardaConfiguracionYValidaCuerpo() throws Exception {
        mvc.perform(put(CONFIG).contentType(MediaType.APPLICATION_JSON).content(CONFIG_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opciones").value(2));
        mvc.perform(put(CONFIG).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void e28GuardaOpcionesYProtegeVersionPublicada() throws Exception {
        when(opciones.guardar(eq(11L), eq(120L), anyList()))
                .thenReturn(List.of(new DatosOpcion("A", 1, "Uno", null)))
                .thenThrow(new IllegalStateException("VERSION_NO_EDITABLE"));
        mvc.perform(put(OPCIONES).contentType(MediaType.APPLICATION_JSON).content(OPCIONES_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opciones[0].letra").value("A"));
        mvc.perform(put(OPCIONES).contentType(MediaType.APPLICATION_JSON).content(OPCIONES_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void e29CargaClaveYDetectaErrorSemantico() throws Exception {
        when(claves.cargar(eq(11L), anyList()))
                .thenReturn(4L)
                .thenThrow(new ClaveInvalidaException());
        mvc.perform(put(CLAVE).contentType(MediaType.APPLICATION_JSON).content(CLAVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idClave").value(4));
        mvc.perform(put(CLAVE).contentType(MediaType.APPLICATION_JSON).content(CLAVE_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CLAVE_INVALIDA"));
    }

    @Test
    void e30CreaBaremoYRechazaRangosInvalidos() throws Exception {
        when(baremos.cargarDatos(any())).thenReturn(2L).thenThrow(new BaremoInvalidoException());
        mvc.perform(post(BAREMO).contentType(MediaType.APPLICATION_JSON).content(BAREMO_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idBaremo").value(2));
        mvc.perform(post(BAREMO).contentType(MediaType.APPLICATION_JSON).content(BAREMO_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("BAREMO_INVALIDO"));
    }

    @Test
    void e31PublicaYRechazaVersionIncompleta() throws Exception {
        when(versiones.publicar(11L, "ACTA-1"))
                .thenReturn(new ResumenVersion(11L, 1L, "PUBLICADA"))
                .thenThrow(new VersionIncompletaException("falta clave"));
        mvc.perform(post(PUBLICAR).contentType(MediaType.APPLICATION_JSON).content(PUBLICAR_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADA"));
        mvc.perform(post(PUBLICAR).contentType(MediaType.APPLICATION_JSON).content(PUBLICAR_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VERSION_INCOMPLETA"));
    }
}
