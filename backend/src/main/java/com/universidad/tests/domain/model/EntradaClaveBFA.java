package com.universidad.tests.domain.model;

/**
 * Entrada de respuesta correcta para una pregunta de seleccion unica.
 *
 * <p>Las relaciones con la version del formulario y la pertenencia de la opcion a la pregunta
 * se comprueban en los servicios de validacion, no en esta entidad aislada.
 */
public class EntradaClaveBFA {

    private final Long idEntradaClave;
    private final Long idClave;
    private final Long idPregunta;
    private final Long idOpcionCorrecta;

    public EntradaClaveBFA(
            Long idEntradaClave, Long idClave, Long idPregunta, Long idOpcionCorrecta) {

        if (idEntradaClave != null && idEntradaClave <= 0) {
            throw new IllegalArgumentException("El ID de la entrada debe ser positivo");
        }

        if (idClave == null || idClave <= 0) {
            throw new IllegalArgumentException("La version de clave es obligatoria y positiva");
        }

        if (idPregunta == null || idPregunta <= 0) {
            throw new IllegalArgumentException("La pregunta es obligatoria y positiva");
        }

        if (idOpcionCorrecta == null || idOpcionCorrecta <= 0) {
            throw new IllegalArgumentException("La opcion correcta es obligatoria y positiva");
        }

        this.idEntradaClave = idEntradaClave;
        this.idClave = idClave;
        this.idPregunta = idPregunta;
        this.idOpcionCorrecta = idOpcionCorrecta;
    }

    public Long getIdEntradaClave() {
        return idEntradaClave;
    }

    public Long getIdClave() {
        return idClave;
    }

    public Long getIdPregunta() {
        return idPregunta;
    }

    public Long getIdOpcionCorrecta() {
        return idOpcionCorrecta;
    }
}
