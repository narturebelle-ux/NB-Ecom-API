package com.narturebelle.controller;

import com.narturebelle.model.NecessityTier;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Product;
import com.narturebelle.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getProducts(
            @RequestParam(required = false) PregnancyStage stage,
            @RequestParam(required = false) NecessityTier tier) {
        if (stage != null && tier != null) {
            return ResponseEntity.ok(productRepository.findByStageAndTier(stage, tier));
        } else if (stage != null) {
            return ResponseEntity.ok(productRepository.findByStage(stage));
        } else if (tier != null) {
            return ResponseEntity.ok(productRepository.findByTier(tier));
        }
        return ResponseEntity.ok(productRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stage/{stage}")
    public ResponseEntity<List<Product>> getProductsByStage(@PathVariable PregnancyStage stage) {
        return ResponseEntity.ok(productRepository.findByStage(stage));
    }
}
