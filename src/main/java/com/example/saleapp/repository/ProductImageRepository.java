package com.example.saleapp.repository;

import com.example.saleapp.dto.ImageViewDTO;
import com.example.saleapp.dto.RowDTO;
import com.example.saleapp.entity.ProductImageEntity;
import com.example.saleapp.entity.ProductsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {
    @Query("""
      select i.productId.productId as productId, i.url as url
      from ProductImageEntity i
      where i.productId.productId in :ids and i.sort = 1
    """)
    List<RowDTO> findMainForProductIds(@Param("ids") List<Long> ids);

    List<ImageViewDTO> findByProductId_ProductIdOrderBySortAsc(Long productId);

    Optional<ProductImageEntity> findByProductId_ProductIdAndSort(Long productId, Integer sort);

}