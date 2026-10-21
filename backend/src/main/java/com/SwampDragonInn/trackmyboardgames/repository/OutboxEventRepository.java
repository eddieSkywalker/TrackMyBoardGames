package com.SwampDragonInn.trackmyboardgames.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.SwampDragonInn.trackmyboardgames.domain.OutboxEvent;

// Internal persistence only: outbox payloads must not be exposed as a user-facing API.
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>
{
}
