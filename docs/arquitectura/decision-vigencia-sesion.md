# Vigencia de sesiones E14

Decisión de Nelson Lazo Rocha, 25-nov-2026: sesiones de dos horas, configurables.

La propiedad `seguridad.sesion.duracion` acepta una duración ISO-8601 positiva; el valor predeterminado es `PT2H`. E14 persiste y devuelve la misma fecha `expiraEn`. Bearer deja de aceptar el token al llegar a esa fecha; E15 puede revocarlo antes. Cambiar la duración afecta las nuevas sesiones.

Esta decisión no define la recuperación de contraseña E16/E17 ni concede roles o permisos. Esas partes conservan sus pendientes. No se incluyen cuentas o credenciales de demo.
