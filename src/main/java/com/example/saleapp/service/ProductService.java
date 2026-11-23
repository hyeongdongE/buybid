package com.example.saleapp.service;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.*;
import com.example.saleapp.entity.*;
import com.example.saleapp.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductsRepository productsRepository;
    private final CategoriesRepository categoriesRepository;

    private final ProductImageRepository productImageRepository;
    private final BidProxyRepository bidProxyRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final TransactionTemplate auctionTransaction;

    private final ProductImageStorage imageStorage;

    @Transactional
    public ResponseEntity<String> productUpload(ProductDTO dto, CustomUserDetail user) {
        LocalDateTime now = auctionNow();
        validateUpload(dto, now);
        CategoriesEntity category = categoriesRepository.findByName(dto.getCategory())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "카테고리를 선택하세요."));

        Path staged = null;
        try {
            staged = imageStorage.stage();
            for (int index = 0; index < dto.getImages().size(); index++) {
                imageStorage.thumbnail(dto.getImages().get(index), staged.resolve(dto.getOrders().get(index) + ".jpg"));
            }
            ProductsEntity product = productsRepository.save(ProductsEntity.builder()
                    .title(dto.getTitle().strip()).content(dto.getDescription().strip()).cateId(category)
                    .startAt(dto.getStartAt()).endAt(dto.getEndAt()).startPrice(dto.getStartPrice())
                    .minIncrement(dto.getMinIncrement()).buyNow(dto.getBuyNow())
                    .status(dto.getStartAt().isAfter(now) ? StatusEnum.SCHEDULED : StatusEnum.RUNNING)
                    .sellerIdx(user.getUser()).build());

            List<ProductImageEntity> images = dto.getOrders().stream().map(sort -> ProductImageEntity.builder()
                    .url("/files/products/" + product.getProductId() + "_400/" + sort + ".jpg")
                    .sort(sort).productId(product).build()).toList();
            productImageRepository.saveAllAndFlush(images);
            imageStorage.publish(staged, product.getProductId());
            return ResponseEntity.ok("등록 성공");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다.", exception);
        } finally {
            if (staged != null) imageStorage.clean(staged);
        }
    }

    private void validateUpload(ProductDTO dto, LocalDateTime now) {
        if (dto.getTitle() == null || dto.getTitle().isBlank() || dto.getTitle().strip().length() > 80
                || dto.getDescription() == null || dto.getDescription().isBlank() || dto.getDescription().strip().length() > 255
                || dto.getCategory() == null || dto.getCategory().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 제목, 설명 및 카테고리를 확인하세요.");
        }
        if (dto.getStartAt() == null || dto.getEndAt() == null || !dto.getEndAt().isAfter(dto.getStartAt())
                || !dto.getEndAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "경매 시작 및 종료 시간을 확인하세요.");
        }
        if (dto.getStartPrice() == null || dto.getStartPrice() < 0 || dto.getMinIncrement() == null
                || dto.getMinIncrement() <= 0 || (dto.getBuyNow() != null
                && dto.getBuyNow() < (long) dto.getStartPrice() + dto.getMinIncrement())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "경매 금액을 확인하세요.");
        }
        if (dto.getImages() == null || dto.getImages().isEmpty() || dto.getImages().size() > 10
                || dto.getOrders() == null || dto.getOrders().size() != dto.getImages().size()
                || !new HashSet<>(dto.getOrders()).equals(
                        new HashSet<>(java.util.stream.IntStream.rangeClosed(1, dto.getImages().size()).boxed().toList()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "1~10장의 이미지와 이미지 순서를 확인하세요.");
        }
    }

    public ProductPage productList(int page, int requestedSize, String category, String sort) {
        if (page < 0 || requestedSize < 1 || (long) page * Math.min(requestedSize, 50) > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 페이지 범위가 올바르지 않습니다.");
        }
        Sort ordering = switch (sort) {
            case "priceAsc" -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("productId"));
            case "priceDesc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("productId"));
            case "endSoon" -> Sort.by(Sort.Order.asc("endAt"), Sort.Order.desc("productId"));
            case "recent" -> Sort.by(Sort.Order.desc("productId"));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 상품 정렬입니다.");
        };
        var results = productsRepository.findCatalog(category == null || category.isBlank() ? null : category,
                PageRequest.of(page, Math.min(requestedSize, 50), ordering));
        var now = auctionNow();
        var ids = results.stream().map(ProductCardRow::getProductId).toList();
        Map<Long, String> images = ids.isEmpty() ? Map.of() : productImageRepository.findMainForProductIds(ids).stream()
                .collect(Collectors.toMap(RowDTO::getProductId, RowDTO::getUrl));
        var cards = results.stream().map(product -> ProductCardDTO.builder()
                .id(product.getProductId()).status(product.getStatus()).startPrice(product.getPrice())
                .title(product.getTitle()).startAt(product.getStartAt()).endAt(product.getEndAt())
                .category(product.getCategory()).ddayText(changeDay(product.getEndAt(), now))
                .imageUrl(images.getOrDefault(product.getProductId(), "/image/default_product.png")).build()).toList();
        return new ProductPage(cards, results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
    }

    // 상품 상세페이지
    public String productdetail(Long productId, Model model) {
        ProductsEntity product = productsRepository.findById(productId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
        UsersEntity user = product.getSellerIdx();
        List<ImageViewDTO> sorted = productImageRepository.findByProductId_ProductIdOrderBySortAsc(productId)
                .stream()
                .sorted(Comparator.comparing(ImageViewDTO::getSort))
                .toList();

        ProductDetailDTO dto = ProductDetailDTO.builder()
                .id(productId)
                .title(product.getTitle())
                .userName(user.getName())
                .url(sorted)
                .description(product.getContent())
                .startPrice(product.getStartPrice())
                .currentPrice(product.getCurrentPrice())
                .minIncrement(product.getMinIncrement())
                .buyNowPrice(product.getBuyNow())
                .leaderName((product.getLeaderIdx() != null) ? product.getLeaderIdx().getName() : "없음")
                .build();
        model.addAttribute("dto", dto);
        return "product_detail";
    }

    @Transactional
    public BidDTO bidProxy(Long productId, BidRequestDTO dto, CustomUserDetail user) {
        ProductsEntity product = lockOpenAuction(productId, user);
        int floor = product.getCurrentPrice() == null ? product.getStartPrice() : product.getCurrentPrice();
        if (dto.getMaxAmount() < floor || dto.getMaxAmount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재가 이상의 최대 입찰가를 입력하세요.");
        }
        if (product.getBuyNow() != null && dto.getMaxAmount() >= product.getBuyNow()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "최대 입찰가는 즉시 구매가보다 낮아야 합니다.");
        }
        BidProxyEntity proxy = bidProxyRepository.findByProductId_ProductIdAndUserId_UserIdx(
                productId, user.getUser().getUserIdx()).orElseGet(() ->
                BidProxyEntity.builder().productId(product).userId(user.getUser()).build());
        if (proxy.getMaxPrice() != null && dto.getMaxAmount() < proxy.getMaxPrice()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "등록된 최대 입찰가보다 낮출 수 없습니다.");
        }

        if (proxy.getMaxPrice() == null || dto.getMaxAmount() > proxy.getMaxPrice()) {
            proxy.setRankOrder(Math.addExact(bidProxyRepository.lastRankOrder(productId), 1));
            proxy.setMaxPrice(dto.getMaxAmount());
            bidProxyRepository.saveAndFlush(proxy);
        }
        List<BidProxyEntity> ranking =
                bidProxyRepository.findTop2ByProductId_ProductIdOrderByMaxPriceDescRankOrderAsc(productId);
        BidProxyEntity leader = ranking.getFirst();
        int price = product.getStartPrice();
        if (ranking.size() == 2) {
            long nextPrice = (long) ranking.get(1).getMaxPrice() + product.getMinIncrement();
            price = (int) Math.max(price, Math.min(leader.getMaxPrice(), nextPrice));
        }
        product.setCurrentPrice(price);
        product.setLeaderIdx(leader.getUserId());

        boolean leads = leader.getUserId().getUserIdx().equals(user.getUser().getUserIdx());
        return BidDTO.builder().leaderName(leader.getUserId().getName()).currentPrice(price)
                .message(leads ? "입찰을 등록했습니다. 현재 최고 입찰자입니다." : "입찰을 등록했습니다. 기존 최고 입찰자가 유지됩니다.")
                .build();
    }

    @Transactional
    public BuyNowDTO buynow(Long productId, CustomUserDetail user) {
        ProductsEntity product = lockOpenAuction(productId, user);
        if (product.getBuyNow() == null || product.getBuyNow() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "즉시 구매가 설정되지 않았습니다.");
        }
        product.setLeaderIdx(user.getUser());
        product.setCurrentPrice(product.getBuyNow());
        ChatRoomEntity room = finishAuction(product);

        return BuyNowDTO.builder()
                .roomId(room.getChatRoomId())
                .message("즉시 구매가 확정되었습니다.")
                .build();
    }

    private ProductsEntity lockOpenAuction(Long productId, CustomUserDetail user) {
        ProductsEntity product = productsRepository.findForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
        if (product.getSellerIdx().getUserIdx().equals(user.getUser().getUserIdx())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 상품에 입찰하거나 구매할 수 없습니다.");
        }
        LocalDateTime now = auctionNow();
        if (product.getStatus() != StatusEnum.RUNNING || now.isBefore(product.getStartAt())
                || !now.isBefore(product.getEndAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 경매만 이용할 수 있습니다.");
        }
        return product;
    }

    @Scheduled(fixedDelayString = "${app.scheduling.delay-ms:60000}")
    public void productScheduler() {
        LocalDateTime now = auctionNow();
        for (StatusEnum status : List.of(StatusEnum.RUNNING, StatusEnum.SCHEDULED)) {
            List<Long> ids = productsRepository.findDueIds(status, now, PageRequest.of(0, 100));
            for (Long id : ids) {
                try {
                    auctionTransaction.executeWithoutResult(transaction -> updateAuctionStatus(id));
                } catch (RuntimeException exception) {
                    log.error("경매 상태 갱신 실패. productId={}", id, exception);
                }
            }
        }
    }

    private void updateAuctionStatus(Long productId) {
        ProductsEntity product = productsRepository.findForUpdate(productId).orElse(null);
        if (product == null || (product.getStatus() != StatusEnum.SCHEDULED
                && product.getStatus() != StatusEnum.RUNNING)) {
            return;
        }
        LocalDateTime now = auctionNow();
        if (!now.isBefore(product.getEndAt())) {
            finishAuction(product);
        } else if (product.getStatus() == StatusEnum.SCHEDULED && !now.isBefore(product.getStartAt())) {
            product.setStatus(StatusEnum.RUNNING);
        }
    }

    private ChatRoomEntity finishAuction(ProductsEntity product) {
        product.setStatus(StatusEnum.ENDED);
        product.setWinnerIdx(product.getLeaderIdx());
        return product.getWinnerIdx() == null ? null : tradeRoom(product);
    }

    private ChatRoomEntity tradeRoom(ProductsEntity product) {
        UsersEntity buyer = product.getWinnerIdx();
        return chatRoomRepository.findByProductId_ProductId(product.getProductId()).map(room -> {
            if (!room.getBuyerId().getUserIdx().equals(buyer.getUserIdx())
                    || !room.getSellarId().getUserIdx().equals(product.getSellerIdx().getUserIdx())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "거래 채팅방의 참여자가 일치하지 않습니다.");
            }
            return room;
        }).orElseGet(() -> chatRoomRepository.save(ChatRoomEntity.builder()
                .buyerId(buyer).sellarId(product.getSellerIdx()).productId(product).build()));
    }

    private LocalDateTime auctionNow() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public String changeDay(LocalDateTime endAt, LocalDateTime now) {
        if (endAt == null || !endAt.isAfter(now)) return "종료";

        Duration left = Duration.between(now, endAt);
        long days = left.toDays();
        if (days >= 1) return "D-" + days;

        long hours   = left.toHoursPart();
        long minutes = left.toMinutesPart();
        return String.format("%dh %02dm", hours, minutes);
    }
}
