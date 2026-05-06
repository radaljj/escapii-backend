package com.escapii.service;

import com.escapii.model.AppError;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface AppErrorService {

    /**
     * Beleži grešku. Ako ista greška (endpoint + tip) već postoji i nije rešena,
     * samo povećava count. Ako je nova — čuva u bazi i šalje email alert.
     */
    void record(HttpServletRequest request, int statusCode, Exception ex);

    List<AppError> getAll();

    long countUnresolved();

    void resolve(Long id);

    void deleteResolved();

    void deleteAll();
}
