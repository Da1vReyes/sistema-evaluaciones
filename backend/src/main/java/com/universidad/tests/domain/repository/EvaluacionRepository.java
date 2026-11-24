package com.universidad.tests.domain.repository;

import com.universidad.tests.domain.model.Evaluacion;
import com.universidad.tests.domain.model.EvaluacionSubprueba;
import java.util.Optional;

public interface EvaluacionRepository {

    // Devuelve la evaluacion con sus subpruebas ordenadas por orden de aplicacion
    Optional<Evaluacion> buscarPorId(Long idEvaluacion);

    // Requiere una transacción de escritura. Serializa cambios del motor por evaluación.
    Optional<Evaluacion> buscarPorIdParaActualizar(Long idEvaluacion);

    void iniciarSubprueba(EvaluacionSubprueba subprueba);

    // Inserta (id nulo) la evaluacion y sus subpruebas
    Evaluacion guardar(Evaluacion evaluacion);
}
