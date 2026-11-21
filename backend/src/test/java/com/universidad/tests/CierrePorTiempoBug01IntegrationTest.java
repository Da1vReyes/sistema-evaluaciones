package com.universidad.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universidad.tests.application.service.DespachoCalculoSubpruebaService;
import com.universidad.tests.application.service.MotorSubpruebasService;
import com.universidad.tests.config.TemporizadorSubpruebasConfig;
import com.universidad.tests.domain.port.AutorizacionEvaluacionPort;
import com.universidad.tests.domain.repository.EvaluacionRepository;
import com.universidad.tests.infrastructure.security.AutorizacionEvaluacionDesarrolloAdapter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** BUG-01 / TC-GEN-03-019: transacciones reales y consultas SQL después de cada petición. */
@SpringBootTest(properties = "motor.temporizador.habilitado=false")
@AutoConfigureMockMvc
@ActiveProfiles("desarrollo-sin-autenticacion")
class CierrePorTiempoBug01IntegrationTest {
    private static final Instant INICIO = Instant.parse("2026-11-21T15:00:00Z");
    private static final Instant LIMITE = INICIO.plusSeconds(300);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MotorSubpruebasService motor;
    @Autowired EvaluacionRepository evaluaciones;
    @Autowired DespachoCalculoSubpruebaService despacho;
    @Autowired Map<String, AutorizacionEvaluacionPort> autorizaciones;
    @MockBean Clock reloj;

    private CatalogoMotorFixture catalogo;
    private String id;

    @BeforeEach
    void preparar() {
        when(reloj.instant()).thenReturn(INICIO);
        id = "BUG01-" + UUID.randomUUID();
        catalogo = CatalogoMotorFixture.crear(jdbc);
        catalogo.evaluacion(jdbc, id, "VOC1", catalogo.voc1());
        catalogo.agregarSubprueba(jdbc, id, "VOC2", catalogo.voc2());
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM evaluacion WHERE id=?", id);
    }

    @Test
    void perfilExplicitoSeleccionaSoloElAdaptadorDeDesarrollo() {
        assertThat(autorizaciones).hasSize(1);
        assertThat(autorizaciones.values().iterator().next())
                .isInstanceOf(AutorizacionEvaluacionDesarrolloAdapter.class);
    }

    @ParameterizedTest(name = "BUG-01: cierre por {0}, {1}s después del límite")
    @CsvSource({
        "E4,0",
        "E4,30",
        "E5,0",
        "E5,30",
        "E6,0",
        "E6,30",
        "E7,0",
        "E7,30",
        "SIGUIENTE,0",
        "SIGUIENTE,30",
        "TEMPORIZADOR,0",
        "TEMPORIZADOR,30"
    })
    void cierrePersisteUnaVezConLimiteOficialYPermiteContinuar(String entrada, long demora)
            throws Exception {
        mvc.perform(post(ruta("VOC1", "iniciar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finLimite").value(LIMITE.toString()));
        mvc.perform(
                        put(ruta("VOC1", "respuestas"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(respuesta("A")))
                .andExpect(status().isOk());
        var respuestasAntes =
                jdbc.queryForList("SELECT * FROM respuesta_evaluacion WHERE evaluacion_id=?", id);

        when(reloj.instant()).thenReturn(LIMITE.minusNanos(1000));
        assertThat(voc1EnApi().get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT fecha_cierre FROM evaluacion_subprueba WHERE evaluacion_id=? AND codigo='VOC1'",
                                Timestamp.class,
                                id))
                .isNull();

        when(reloj.instant()).thenReturn(LIMITE.plusSeconds(demora));
        switch (entrada) {
            case "E4" -> mvc.perform(get(base())).andExpect(status().isOk());
            case "E5" ->
                    mvc.perform(post(ruta("VOC1", "iniciar")))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.codigo").value("SUBPRUEBA_CERRADA"));
            case "E6" ->
                    mvc.perform(get(ruta("VOC1", "preguntas")))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.codigo").value("SUBPRUEBA_NO_ACTIVA"));
            case "E7" ->
                    mvc.perform(
                                    put(ruta("VOC1", "respuestas"))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(respuesta("B")))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.codigo").value("TIEMPO_AGOTADO"));
            case "SIGUIENTE" ->
                    mvc.perform(post(ruta("VOC2", "iniciar"))).andExpect(status().isOk());
            case "TEMPORIZADOR" ->
                    new TemporizadorSubpruebasConfig(evaluaciones, motor, despacho, reloj)
                            .cerrarVencidas();
            default -> throw new IllegalArgumentException(entrada);
        }
        comprobarCierrePersistido();

        // Una consulta, otro intento de inicio y el temporizador no alteran el cierre original.
        when(reloj.instant()).thenReturn(LIMITE.plusSeconds(60));
        mvc.perform(post(ruta("VOC1", "iniciar")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUBPRUEBA_CERRADA"));
        motor.procesarVencimiento(id);
        comprobarCierrePersistido();
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM respuesta_evaluacion WHERE evaluacion_id=?", id))
                .isEqualTo(respuestasAntes);

        var siguiente =
                mvc.perform(post(ruta("VOC2", "iniciar")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.estado").value("ACTIVA"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        var limiteSiguiente = json.readTree(siguiente).get("finLimite").asText();
        mvc.perform(post(ruta("VOC2", "iniciar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finLimite").value(limiteSiguiente));
        comprobarCierrePersistido();
    }

    private void comprobarCierrePersistido() throws Exception {
        var cierre =
                jdbc.queryForMap(
                        "SELECT id,estado,fecha_cierre,fecha_fin_limite FROM evaluacion_subprueba"
                                + " WHERE evaluacion_id=? AND codigo='VOC1'",
                        id);
        assertThat(cierre.get("estado")).isEqualTo("CERRADA");
        assertThat(((Timestamp) cierre.get("fecha_cierre")).toInstant()).isEqualTo(LIMITE);
        assertThat(cierre.get("fecha_cierre")).isEqualTo(cierre.get("fecha_fin_limite"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM solicitud_calculo_subprueba WHERE id_subprueba=?",
                                Long.class,
                                cierre.get("id")))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT fecha_cierre FROM solicitud_calculo_subprueba WHERE id_subprueba=?",
                                Timestamp.class,
                                cierre.get("id")))
                .isEqualTo(cierre.get("fecha_cierre"));
        var api = voc1EnApi();
        assertThat(api.get("estado").asText()).isEqualTo("CERRADA");
        assertThat(api.get("cerradaEn"))
                .as("E4 expone cerradaEn según el contrato aprobado")
                .isNotNull();
        assertThat(Instant.parse(api.get("cerradaEn").asText())).isEqualTo(LIMITE);
    }

    private JsonNode voc1EnApi() throws Exception {
        var cuerpo =
                mvc.perform(get(base()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        for (JsonNode subprueba : json.readTree(cuerpo).get("subpruebas")) {
            if (subprueba.get("codigo").asText().equals("VOC1")) return subprueba;
        }
        throw new AssertionError("VOC1 debe aparecer en E4");
    }

    private String respuesta(String opcion) {
        return "{\"respuestas\":[{\"preguntaId\":"
                + catalogo.pregunta1()
                + ",\"opcion\":\""
                + opcion
                + "\"}]}";
    }

    private String base() {
        return "/api/v1/evaluaciones/" + id;
    }

    private String ruta(String codigo, String recurso) {
        return base() + "/subpruebas/" + codigo + "/" + recurso;
    }
}
