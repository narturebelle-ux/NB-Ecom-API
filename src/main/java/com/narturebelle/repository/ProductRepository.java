package com.narturebelle.repository;

import com.narturebelle.model.NecessityTier;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStage(PregnancyStage stage);
    List<Product> findByStageAndTier(PregnancyStage stage, NecessityTier tier);
    List<Product> findByTier(NecessityTier tier);
}
