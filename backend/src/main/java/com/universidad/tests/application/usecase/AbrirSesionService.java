package com.universidad.tests.application.usecase;

import com.universidad.tests.application.service.AutenticacionService;
import com.universidad.tests.application.service.SesionAbierta;
import com.universidad.tests.dto.request.InicioSesionRequest;
import com.universidad.tests.dto.response.SesionResponse;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class AbrirSesionService {

    private final AutenticacionService autenticacion;

    public AbrirSesionService(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    public SesionResponse abrir(InicioSesionRequest solicitud) {
        char[] contrasena = solicitud.contrasena().toCharArray();
        try {
            SesionAbierta sesion = autenticacion.abrirSesion(solicitud.correo(), contrasena);
            if (sesion.expiraEn() == null) {
                // El contrato exige una fecha; la dependencia debe definir la vigencia.
                // No entregar una sesion ilimitada como respuesta de exito.
                autenticacion.cerrarSesion(sesion.token());
                throw new IllegalStateException("La vigencia de la sesion no esta configurada");
            }
            return new SesionResponse(
                    sesion.token(), sesion.expiraEn(), sesion.rolActivo(), sesion.permisos());
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }
}
