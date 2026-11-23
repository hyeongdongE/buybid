package com.example.saleapp.repository;

import com.example.saleapp.dto.ProductCardRow;
import com.example.saleapp.entity.ProductsEntity;
import com.example.saleapp.entity.StatusEnum;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductsRepository extends JpaRepository<ProductsEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductsEntity p where p.productId = :id")
    Optional<ProductsEntity> findForUpdate(Long id);

    @Query("""
            select p.productId from ProductsEntity p
            where p.status = :status
              and ((p.status = com.example.saleapp.entity.StatusEnum.RUNNING and p.endAt <= :now)
                or (p.status = com.example.saleapp.entity.StatusEnum.SCHEDULED and p.startAt <= :now))
            order by p.productId
            """)
    List<Long> findDueIds(StatusEnum status, LocalDateTime now, Pageable limit);

    @Query(value = """
            select p.productId as productId, p.status as status, coalesce(p.currentPrice, p.startPrice) as price,
                   p.title as title, p.startAt as startAt, p.endAt as endAt, c.name as category
            from ProductsEntity p left join p.cateId c
            where :category is null or c.name = :category
            """, countQuery = """
            select count(p) from ProductsEntity p left join p.cateId c
            where :category is null or c.name = :category
            """)
    Page<ProductCardRow> findCatalog(String category, Pageable page);
}
