package com.richcomes.garage.service.repository;

import com.richcomes.garage.service.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    // Spring Data JPA මගින් මූලික CRUD ක්‍රියාකාරකම් (Save, Find, Delete) ඉබේම ලබා දෙයි
}