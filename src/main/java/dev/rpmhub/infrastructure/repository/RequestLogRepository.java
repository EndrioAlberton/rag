/**
 * This file contains confidential and proprietary information.
 * Unauthorized copying, distribution, or use of this file or its contents is
 * strictly prohibited.
 *
 * 2025 Rodrigo Prestes Machado. All rights reserved.
 */
package dev.rpmhub.infrastructure.repository;

import dev.rpmhub.domain.model.RequestLog;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for RequestLog entity.
 */
@ApplicationScoped
public class RequestLogRepository implements PanacheRepositoryBase<RequestLog, String> {

    public Uni<List<RequestLog>> findAllOrderedByTimestamp() {
        return find("ORDER BY messageTimestamp ASC").list();
    }
}
