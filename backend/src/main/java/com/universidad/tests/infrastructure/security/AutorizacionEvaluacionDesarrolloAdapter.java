package com.universidad.tests.infrastructure.security;

import com.universidad.tests.domain.port.AutorizacionEvaluacionPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Solo para desarrollo y pruebas manuales autorizadas; nunca activar en producción ni en el CI
 * normal. TODO(GEN-06): eliminar este adaptador al integrar la autorización real.
 */
@Component
@Profile("desarrollo-sin-autenticacion")
public class AutorizacionEvaluacionDesarrolloAdapter implements AutorizacionEvaluacionPort {
    @Override
    public void exigirAcceso(String evaluacionId) {
        // Habilitación explícita y temporal para probar E4 a E7 sin sustituir GEN-06.
    }
}
