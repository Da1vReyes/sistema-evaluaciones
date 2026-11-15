package com.universidad.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.universidad.tests.domain.model.EstadoVersionClaveBFA;
import com.universidad.tests.domain.model.VersionClaveBFA;
import org.junit.jupiter.api.Test;

class VersionClaveBFADominioTest {

    private VersionClaveBFA crearClave() {
        return new VersionClaveBFA(10L, 5L, "CLAVE-V1", "MATERIAL-SINTETICO");
    }

    // 1. Una nueva clave inicia en BORRADOR
    @Test
    void debeCrearClaveEnBorrador() {
        VersionClaveBFA clave = crearClave();

        assertEquals(EstadoVersionClaveBFA.BORRADOR, clave.getEstado());

        assertTrue(clave.esEditable());
        assertNull(clave.getEvidenciaAprobacion());
    }

    // 2. Aprobar una clave con evidencia
    @Test
    void debeAprobarClaveConEvidencia() {
        VersionClaveBFA clave = crearClave();

        clave.aprobar("APROBACION-SINTETICA-001");

        assertEquals(EstadoVersionClaveBFA.APROBADA, clave.getEstado());

        assertEquals("APROBACION-SINTETICA-001", clave.getEvidenciaAprobacion());

        assertFalse(clave.esEditable());
    }

    // 3. Rechazar aprobacion sin evidencia
    @Test
    void debeRechazarAprobacionSinEvidencia() {
        VersionClaveBFA clave = crearClave();

        assertThrows(IllegalArgumentException.class, () -> clave.aprobar(null));

        assertThrows(IllegalArgumentException.class, () -> clave.aprobar("   "));

        assertEquals(EstadoVersionClaveBFA.BORRADOR, clave.getEstado());

        assertNull(clave.getEvidenciaAprobacion());
    }

    // 4. Rechazar aprobacion de clave no guardada
    @Test
    void debeRechazarAprobacionDeClaveSinId() {
        VersionClaveBFA clave = new VersionClaveBFA(null, 5L, "CLAVE-V1", "MATERIAL-SINTETICO");

        assertThrows(IllegalStateException.class, () -> clave.aprobar("EVIDENCIA-SINTETICA"));

        assertEquals(EstadoVersionClaveBFA.BORRADOR, clave.getEstado());
    }

    // 5. Impedir aprobar nuevamente una clave
    @Test
    void debeRechazarDobleAprobacion() {
        VersionClaveBFA clave = crearClave();

        clave.aprobar("APROBACION-ORIGINAL");

        assertThrows(IllegalStateException.class, () -> clave.aprobar("OTRA-APROBACION"));

        assertEquals("APROBACION-ORIGINAL", clave.getEvidenciaAprobacion());
    }

    // 6. Retirar una clave aprobada
    @Test
    void debeRetirarClaveAprobada() {
        VersionClaveBFA clave = crearClave();

        clave.aprobar("APROBACION-SINTETICA");
        clave.retirar();

        assertEquals(EstadoVersionClaveBFA.RETIRADA, clave.getEstado());

        assertFalse(clave.esEditable());

        // Conservar evidencia historica
        assertEquals("APROBACION-SINTETICA", clave.getEvidenciaAprobacion());
    }

    // 7. Impedir retirar una clave en BORRADOR
    @Test
    void debeRechazarRetiroDeClaveEnBorrador() {
        VersionClaveBFA clave = crearClave();

        assertThrows(IllegalStateException.class, clave::retirar);

        assertEquals(EstadoVersionClaveBFA.BORRADOR, clave.getEstado());
    }

    // 8. Impedir retirar dos veces una clave
    @Test
    void debeRechazarDobleRetiro() {
        VersionClaveBFA clave = crearClave();

        clave.aprobar("APROBACION-SINTETICA");
        clave.retirar();

        assertThrows(IllegalStateException.class, clave::retirar);

        assertEquals(EstadoVersionClaveBFA.RETIRADA, clave.getEstado());
    }

    // 9. Impedir volver a aprobar una clave retirada
    @Test
    void debeRechazarReaprobacionDeClaveRetirada() {
        VersionClaveBFA clave = crearClave();

        clave.aprobar("APROBACION-ORIGINAL");
        clave.retirar();

        assertThrows(IllegalStateException.class, () -> clave.aprobar("NUEVA-APROBACION"));

        assertEquals(EstadoVersionClaveBFA.RETIRADA, clave.getEstado());

        assertEquals("APROBACION-ORIGINAL", clave.getEvidenciaAprobacion());
    }
}
