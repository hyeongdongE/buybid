package com.example.saleapp;

import com.example.saleapp.entity.CategoriesEntity;
import com.example.saleapp.entity.ProductsEntity;
import com.example.saleapp.entity.StatusEnum;
import com.example.saleapp.repository.CategoriesRepository;
import com.example.saleapp.repository.ProductsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ProductsRepository products;
    @Autowired CategoriesRepository categories;

    @Test
    void catalogFiltersAndSortsBeforeApplyingThePageLimit() throws Exception {
        var category = categories.save(CategoriesEntity.builder().name(UUID.randomUUID().toString()).build());
        products.saveAll(IntStream.rangeClosed(1, 52).mapToObj(i -> ProductsEntity.builder()
                .cateId(category).title("item " + i).startPrice(i * 100).minIncrement(100)
                .endAt(LocalDateTime.of(2035, 1, 1, 12, 0)).status(StatusEnum.RUNNING).build()).toList());
        mvc.perform(get("/api/products").param("category", category.getName()).param("sort", "priceAsc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(52))
                .andExpect(jsonPath("$.content.length()").value(24))
                .andExpect(jsonPath("$.content[0].startPrice").value(100));
        mvc.perform(get("/api/products").param("category", category.getName()).param("sort", "priceAsc")
                        .param("page", "1").param("size", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].startPrice").value(5100));
    }
}
