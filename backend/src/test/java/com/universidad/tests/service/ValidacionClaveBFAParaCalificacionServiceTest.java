package com.universidad.tests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.universidad.tests.application.service.ValidacionClaveBFAParaCalificacionService;
import com.universidad.tests.domain.model.EstadoVersionClaveBFA;
import com.universidad.tests.domain.model.VersionClaveBFA;
import com.universidad.tests.domain.port.AutorizacionClaveBFAPort;
import com.universidad.tests.domain.repository.VersionClaveBFARepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidacionClaveBFAParaCalificacionServiceTest {

    @Mock private VersionClaveBFARepository claveRepository;

    @Mock private AutorizacionClaveBFAPort autorizacion;

    @InjectMocks private ValidacionClaveBFAParaCalificacionService servicio;

    // =====================================================
    // DATOS SINTETICOS PARA LAS PRUEBAS
    // =====================================================

    private VersionClaveBFA claveBorrador() {

        return new VersionClaveBFA(10L, 5L, "CLAVE-V1", "MATERIAL-SINTETICO");
    }

    private VersionClaveBFA claveAprobada() {

        return VersionClaveBFA.restaurar(
                10L,
                5L,
                "CLAVE-V1",
                "MATERIAL-SINTETICO",
                EstadoVersionClaveBFA.APROBADA,
                "EVIDENCIA-OFICIAL-SINTETICA");
    }

    private VersionClaveBFA claveRetirada() {

        return VersionClaveBFA.restaurar(
                10L,
                5L,
                "CLAVE-V1",
                "MATERIAL-SINTETICO",
                EstadoVersionClaveBFA.RETIRADA,
                "EVIDENCIA-OFICIAL-SINTETICA");
    }

    // =====================================================
    // 1. PERMITIR CLAVE APROBADA
    // =====================================================

    @Test
    void debePermitirClaveAprobadaDelFormularioCorrecto() {

        VersionClaveBFA clave = claveAprobada();

        when(claveRepository.buscarPorId(10L)).thenReturn(Optional.of(clave));

        VersionClaveBFA resultado = servicio.obtenerClaveAprobada(10L, 5L);

        assertSame(clave, resultado);

        assertEquals(EstadoVersionClaveBFA.APROBADA, resultado.getEstado());

        verify(autorizacion).exigirPermisoCalificarConClave(10L);

        verify(claveRepository).buscarPorId(10L);
    }

    // =====================================================
    // 2. RECHAZAR CLAVE EN BORRADOR
    // =====================================================

    @Test
    void debeRechazarClaveEnBorrador() {

        when(claveRepository.buscarPorId(10L)).thenReturn(Optional.of(claveBorrador()));

        IllegalStateException excepcion =
                assertThrows(
                        IllegalStateException.class, () -> servicio.obtenerClaveAprobada(10L, 5L));

        assertTrue(excepcion.getMessage().contains("Solo una clave APROBADA"));

        verify(claveRepository).buscarPorId(10L);
    }

    // =====================================================
    // 3. RECHAZAR CLAVE RETIRADA
    // =====================================================

    @Test
    void debeRechazarClaveRetirada() {

        when(claveRepository.buscarPorId(10L)).thenReturn(Optional.of(claveRetirada()));

        IllegalStateException excepcion =
                assertThrows(
                        IllegalStateException.class, () -> servicio.obtenerClaveAprobada(10L, 5L));

        assertTrue(excepcion.getMessage().contains("Solo una clave APROBADA"));

        verify(claveRepository).buscarPorId(10L);
    }

    // =====================================================
    // 4. RECHAZAR CLAVE DE OTRO FORMULARIO
    // =====================================================

    @Test
    void debeRechazarClaveDeFormularioDiferente() {

        when(claveRepository.buscarPorId(10L)).thenReturn(Optional.of(claveAprobada()));

        IllegalStateException excepcion =
                assertThrows(
                        IllegalStateException.class, () -> servicio.obtenerClaveAprobada(10L, 8L));

        assertTrue(
                excepcion
                        .getMessage()
                        .contains("no pertenece a la version " + "de formulario indicada"));

        verify(claveRepository).buscarPorId(10L);
    }

    // =====================================================
    // 5. RECHAZAR CLAVE INEXISTENTE
    // =====================================================

    @Test
    void debeRechazarClaveInexistente() {

        when(claveRepository.buscarPorId(10L)).thenReturn(Optional.empty());

        NoSuchElementException excepcion =
                assertThrows(
                        NoSuchElementException.class, () -> servicio.obtenerClaveAprobada(10L, 5L));

        assertEquals("No existe la clave BFA indicada", excepcion.getMessage());

        verify(claveRepository).buscarPorId(10L);
    }

    // =====================================================
    // 6. RECHAZAR ID DE CLAVE INVALIDO
    // =====================================================

    @Test
    void debeRechazarIdentificadorDeClaveInvalido() {

        assertThrows(IllegalArgumentException.class, () -> servicio.obtenerClaveAprobada(null, 5L));

        assertThrows(IllegalArgumentException.class, () -> servicio.obtenerClaveAprobada(0L, 5L));

        assertThrows(IllegalArgumentException.class, () -> servicio.obtenerClaveAprobada(-1L, 5L));

        verifyNoInteractions(claveRepository);
        verifyNoInteractions(autorizacion);
    }

    // =====================================================
    // 7. RECHAZAR ID DE FORMULARIO INVALIDO
    // =====================================================

    @Test
    void debeRechazarIdentificadorDeFormularioInvalido() {

        assertThrows(
                IllegalArgumentException.class, () -> servicio.obtenerClaveAprobada(10L, null));

        assertThrows(IllegalArgumentException.class, () -> servicio.obtenerClaveAprobada(10L, 0L));

        assertThrows(IllegalArgumentException.class, () -> servicio.obtenerClaveAprobada(10L, -1L));

        verifyNoInteractions(claveRepository);
        verifyNoInteractions(autorizacion);
    }

    // =====================================================
    // 8. RECHAZAR ACCESO SIN AUTORIZACION
    // =====================================================

    @Test
    void debeRechazarAccesoSinPermisoAntesDeConsultarClave() {

        // Simular una autorizacion denegada.
        doThrow(new SecurityException("Operacion no autorizada: permiso BFA denegado"))
                .when(autorizacion)
                .exigirPermisoCalificarConClave(10L);

        // El servicio debe detenerse inmediatamente.
        SecurityException excepcion =
                assertThrows(SecurityException.class, () -> servicio.obtenerClaveAprobada(10L, 5L));

        assertTrue(excepcion.getMessage().contains("permiso BFA denegado"));

        // Comprobar que se exigio el permiso.
        verify(autorizacion).exigirPermisoCalificarConClave(10L);

        // La base de datos NO debe ser consultada.
        verifyNoInteractions(claveRepository);
    }
}
