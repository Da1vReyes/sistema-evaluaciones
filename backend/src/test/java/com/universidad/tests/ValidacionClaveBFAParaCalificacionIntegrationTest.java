package com.universidad.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.universidad.tests.application.service.ValidacionClaveBFAParaCalificacionService;
import com.universidad.tests.domain.model.EstadoVersionClaveBFA;
import com.universidad.tests.domain.model.VersionClaveBFA;
import com.universidad.tests.domain.port.AutorizacionClaveBFAPort;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ValidacionClaveBFAParaCalificacionIntegrationTest {

    private static final String EVIDENCIA = "APROBACION-SINTETICA-CALIFICACION";

    @Autowired private ValidacionClaveBFAParaCalificacionService servicio;

    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AutorizacionClaveBFAPort autorizacion;

    private Long idFormulario;
    private Long idClave;

    // =====================================================
    // PREPARAR DATOS EN LA BASE DE PRUEBAS
    // =====================================================

    @BeforeEach
    void prepararDatosSinteticos() {

        String base = jdbc.queryForObject("SELECT current_database()", String.class);

        if (!"evaluaciones_bfa_test".equals(base)) {
            throw new IllegalStateException(
                    "Estas pruebas solo pueden ejecutarse " + "en evaluaciones_bfa_test");
        }

        String codigo = "BFA_CAL_" + UUID.randomUUID().toString().replace("-", "");

        Long idTest =
                jdbc.queryForObject(
                        "INSERT INTO test "
                                + "(codigo, nombre, descripcion, activo) "
                                + "VALUES (?, ?, ?, ?) "
                                + "RETURNING id_test",
                        Long.class,
                        codigo,
                        "Test sintetico de calificacion BFA",
                        "Prueba de integracion",
                        true);

        idFormulario =
                jdbc.queryForObject(
                        "INSERT INTO version_formulario "
                                + "(id_test, codigo_version, descripcion, fuente_ref) "
                                + "VALUES (?, ?, ?, ?) "
                                + "RETURNING id_version_formulario",
                        Long.class,
                        idTest,
                        "v1",
                        "Formulario sintetico",
                        "FUENTE-SINTETICA");

        idClave =
                jdbc.queryForObject(
                        "INSERT INTO version_clave_bfa "
                                + "(id_version_formulario, version, referencia_fuente) "
                                + "VALUES (?, ?, ?) "
                                + "RETURNING id_clave",
                        Long.class,
                        idFormulario,
                        "CLAVE-V1",
                        "MATERIAL-SINTETICO");
    }

    // =====================================================
    // 1. CLAVE APROBADA
    // =====================================================

    @Test
    void debeAceptarClaveAprobadaDesdePostgreSQL() {

        aprobarClave();

        VersionClaveBFA resultado = servicio.obtenerClaveAprobada(idClave, idFormulario);

        assertEquals(EstadoVersionClaveBFA.APROBADA, resultado.getEstado());

        assertEquals(idClave, resultado.getIdClave());

        assertEquals(idFormulario, resultado.getIdVersionFormulario());

        assertEquals(EVIDENCIA, resultado.getEvidenciaAprobacion());
    }

    // =====================================================
    // 2. CLAVE EN BORRADOR
    // =====================================================

    @Test
    void debeRechazarClaveEnBorradorDesdePostgreSQL() {

        IllegalStateException excepcion =
                assertThrows(
                        IllegalStateException.class,
                        () -> servicio.obtenerClaveAprobada(idClave, idFormulario));

        assertTrue(excepcion.getMessage().contains("Solo una clave APROBADA"));

        assertEquals("BORRADOR", estadoEnBaseDeDatos());
    }

    // =====================================================
    // 3. CLAVE RETIRADA
    // =====================================================

    @Test
    void debeRechazarClaveRetiradaDesdePostgreSQL() {

        aprobarClave();
        retirarClave();

        IllegalStateException excepcion =
                assertThrows(
                        IllegalStateException.class,
                        () -> servicio.obtenerClaveAprobada(idClave, idFormulario));

        assertTrue(excepcion.getMessage().contains("Solo una clave APROBADA"));

        assertEquals("RETIRADA", estadoEnBaseDeDatos());

        assertEquals(EVIDENCIA, evidenciaEnBaseDeDatos());
    }

    // =====================================================
    // 4. CLAVE DE OTRO FORMULARIO
    // =====================================================

    @Test
    void debeRechazarClaveDeOtroFormularioDesdePostgreSQL() {

        aprobarClave();

        Long idFormularioDiferente = idFormulario + 10000L;

        IllegalStateException excepcion =
                assertThrows(
                        IllegalStateException.class,
                        () -> servicio.obtenerClaveAprobada(idClave, idFormularioDiferente));

        assertTrue(excepcion.getMessage().contains("no pertenece a la version"));

        assertEquals("APROBADA", estadoEnBaseDeDatos());
    }

    // =====================================================
    // 5. CLAVE INEXISTENTE
    // =====================================================

    @Test
    void debeRechazarClaveInexistenteDesdePostgreSQL() {

        assertThrows(
                NoSuchElementException.class,
                () -> servicio.obtenerClaveAprobada(Long.MAX_VALUE, idFormulario));
    }

    // =====================================================
    // 6. CLAVE APROBADA SIN AUTORIZACION
    // =====================================================

    @Test
    void debeRechazarAccesoSinPermisoDesdePostgreSQL() {

        // La clave existe y esta aprobada.
        aprobarClave();

        // Simular que el usuario no tiene permiso.
        doThrow(new SecurityException("Operacion no autorizada: permiso BFA denegado"))
                .when(autorizacion)
                .exigirPermisoCalificarConClave(idClave);

        // La solicitud debe ser rechazada.
        SecurityException excepcion =
                assertThrows(
                        SecurityException.class,
                        () -> servicio.obtenerClaveAprobada(idClave, idFormulario));

        assertTrue(excepcion.getMessage().contains("permiso BFA denegado"));

        // Confirmar que se verifico el permiso.
        verify(autorizacion).exigirPermisoCalificarConClave(idClave);

        // La clave sigue aprobada y no fue alterada.
        assertEquals("APROBADA", estadoEnBaseDeDatos());

        // Su evidencia institucional se conserva.
        assertEquals(EVIDENCIA, evidenciaEnBaseDeDatos());
    }

    // =====================================================
    // METODOS AUXILIARES
    // =====================================================

    private void aprobarClave() {

        jdbc.update(
                "UPDATE version_clave_bfa "
                        + "SET estado = 'APROBADA', "
                        + "evidencia_aprobacion = ? "
                        + "WHERE id_clave = ?",
                EVIDENCIA,
                idClave);
    }

    private void retirarClave() {

        jdbc.update(
                "UPDATE version_clave_bfa " + "SET estado = 'RETIRADA' " + "WHERE id_clave = ?",
                idClave);
    }

    private String estadoEnBaseDeDatos() {

        return jdbc.queryForObject(
                "SELECT estado FROM version_clave_bfa " + "WHERE id_clave = ?",
                String.class,
                idClave);
    }

    private String evidenciaEnBaseDeDatos() {

        return jdbc.queryForObject(
                "SELECT evidencia_aprobacion " + "FROM version_clave_bfa " + "WHERE id_clave = ?",
                String.class,
                idClave);
    }
}
