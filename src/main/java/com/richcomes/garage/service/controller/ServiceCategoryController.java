package com.richcomes.garage.service.controller;

import com.richcomes.garage.service.dto.ServiceCategoryDTO;
import com.richcomes.garage.service.service.ServiceCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-categories")
@CrossOrigin(origins = "*")
public class ServiceCategoryController {

    private final ServiceCategoryService service;

    @Autowired
    public ServiceCategoryController(ServiceCategoryService service) {
        this.service = service;
    }

    // අලුත් Category එකක් සෑදීමට (POST Request)
    @PostMapping
    public ResponseEntity<ServiceCategoryDTO> createCategory(@RequestBody ServiceCategoryDTO categoryDTO) {
        return ResponseEntity.ok(service.createCategory(categoryDTO));
    }

    // සියලුම Categories බැලීමට (GET Request)
    @GetMapping
    public ResponseEntity<List<ServiceCategoryDTO>> getAllCategories() {
        return ResponseEntity.ok(service.getAllCategories());
    }

    // ID එක මගින් එක Category එකක් සෙවීමට (GET Request)
    @GetMapping("/{id}")
    public ResponseEntity<ServiceCategoryDTO> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCategoryById(id));
    }

    // Category එකක් වෙනස් කිරීමට (PUT Request)
    @PutMapping("/{id}")
    public ResponseEntity<ServiceCategoryDTO> updateCategory(@PathVariable Long id, @RequestBody ServiceCategoryDTO categoryDTO) {
        return ResponseEntity.ok(service.updateCategory(id, categoryDTO));
    }

    // Category එකක් මකා දැමීමට (DELETE Request)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.ok().build();
    }
}