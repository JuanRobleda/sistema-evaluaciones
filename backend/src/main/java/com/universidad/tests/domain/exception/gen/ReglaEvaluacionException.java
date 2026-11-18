package com.universidad.tests.domain.exception.gen;

public class ReglaEvaluacionException extends RuntimeException {
    private final int status;
    private final String codigo;
    private final Long evaluacionId;

    public ReglaEvaluacionException(int status, String codigo, String mensaje) {
        this(status, codigo, mensaje, null);
    }

    public ReglaEvaluacionException(int status, String codigo, String mensaje, Long id) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
        this.evaluacionId = id;
    }

    public int status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public Long evaluacionId() {
        return evaluacionId;
    }
}
