package br.com.desafio.cardapi.adapters.out.persistence;

import org.springframework.data.cassandra.repository.CassandraRepository;

public interface CassandraCardRepository extends CassandraRepository<CardEntity, String> {
}
