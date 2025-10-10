package com.example.saleapp.service;

import com.example.saleapp.component.JwtTokenProvider;
import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.*;
import com.example.saleapp.entity.*;
import com.example.saleapp.repository.*;
import com.example.saleapp.util.ThumbUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final UsersRepository usersRepository;
    private final ProductImageRepository productImageRepository;
    private final BidProxyRepository bidProxyRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Value("${app.upload-dir}")
    private String uploadDir;

    // 상품 업로드 로직
    // Service: 기존 시그니처 변경 (HttpServletRequest 제거)
    public ResponseEntity<String> productUpload(ProductDTO dto, CustomUserDetail user) throws IOException {
        // 0) 기본 검증
        if (dto.getImages() == null || dto.getOrders() == null
                || dto.getImages().size() != dto.getOrders().size()) {
            return ResponseEntity.badRequest().body("이미지/정렬 정보가 올바르지 않습니다.");
        }
        if (dto.getImages().size() > 10) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("이미지는 최대 10장까지 가능합니다.");
        }
        if (dto.getStartAt() == null || dto.getEndAt() == null || !dto.getEndAt().isAfter(dto.getStartAt())) {
            return ResponseEntity.badRequest().body("시작/종료 시간이 올바르지 않습니다.");
        }

        // 1) 카테고리
        CategoriesEntity category = categoriesRepository.findByName(dto.getCategory())
                .orElseThrow(() -> new NoSuchElementException("해당 카테고리를 찾을 수 없습니다: " + dto.getCategory()));

        // 2) 판매자 (SecurityContext 기반)
        Long sellerId = user.getUser().getUserIdx();
        UsersEntity seller = usersRepository.findById(sellerId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        // 3) 상태 결정 (KST 기준)
        LocalDateTime nowKst = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        StatusEnum status = dto.getStartAt().isAfter(nowKst) ? StatusEnum.SCHEDULED : StatusEnum.RUNNING;

        // 4) 상품 저장
        ProductsEntity product = ProductsEntity.builder()
                .title(dto.getTitle())
                .content(dto.getDescription())
                .cateId(category)
                .startAt(dto.getStartAt())
                .endAt(dto.getEndAt())
                .startPrice(dto.getStartPrice())
                .minIncrement(dto.getMinIncrement())
                .buyNow(dto.getBuyNow())
                .status(status)
                .sellerIdx(seller)
                .build();
        productsRepository.save(product);

        // 5) 디렉토리 준비
        Path productDir = Paths.get(uploadDir, "products", String.valueOf(product.getProductId()));
        Path thumbDir   = Paths.get(uploadDir, "products", product.getProductId() + "_400");
        Files.createDirectories(productDir);
        Files.createDirectories(thumbDir);

        // 6) 파일 저장 & 썸네일 생성
        for (int i = 0; i < dto.getImages().size(); i++) {
            MultipartFile file = dto.getImages().get(i);
            Integer sort = dto.getOrders().get(i);

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("빈 파일이 포함되어 있습니다.");
            }
            // 간단한 MIME 가드(선택)
            String contentType = Optional.ofNullable(file.getContentType()).orElse("");
            if (!contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("이미지 파일만 업로드할 수 있습니다.");
            }

            String original = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
            String ext = Optional.ofNullable(StringUtils.getFilenameExtension(original))
                    .map(String::toLowerCase)
                    .map(s -> s.equals("jpeg") ? "jpg" : s)
                    .map(s -> "." + s)
                    .orElse(".jpg");

            String stored = sort + ext; // 원본은 원래 확장자 유지
            Path originalPath = productDir.resolve(stored);
            file.transferTo(originalPath.toFile());

            // 썸네일은 jpg 고정
            String thumbStored = sort + ".jpg";
            Path thumbPath = thumbDir.resolve(thumbStored);
            try (InputStream in = Files.newInputStream(originalPath)) {
                ThumbUtil.squareThumb(in, thumbPath, 400, "jpg", 0.85);
            }

            String url = "/files/products/" + product.getProductId() + "_400/" + thumbStored;

            ProductImageEntity imageEntity = ProductImageEntity.builder()
                    .url(url)
                    .sort(sort)
                    .productId(product)
                    .build();
            productImageRepository.save(imageEntity);
        }

        return ResponseEntity.ok("등록 성공");
    }

    // 상품 리스트 반환 로직
    public List<ProductCardDTO> prouductList() {
        List<ProductsEntity> products = productsRepository.findAll(
                Sort.by(Sort.Direction.DESC, "productId")
        );
        var now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Long> ids = products.stream().map(ProductsEntity::getProductId).toList();
        Map<Long, String> imgMap = productImageRepository.findMainForProductIds(ids).stream()
                .collect(Collectors.toMap(RowDTO::getProductId,
                        RowDTO::getUrl));

        return products.stream().map(p -> ProductCardDTO.builder()
                        .id(p.getProductId())
                        .status(p.getStatus())
                        .startPrice((p.getCurrentPrice() != null) ? p.getCurrentPrice() : p.getStartPrice())
                        .title(p.getTitle())
                        .startAt(p.getStartAt())
                        .endAt(p.getEndAt())
                        .category(p.getCateId().getName())
                        .imageUrl(imgMap.getOrDefault(p.getProductId(), "/image/default_product.png"))
                        .ddayText(changeDay(p.getEndAt(), now))
                        .build())
                .toList();
    }

    // 상품 상세페이지
    public String productdetail(Long productId, Model model) {
        ProductsEntity product = productsRepository.findById(productId).orElseThrow();
        UsersEntity user = usersRepository.findById(product.getSellerIdx().getUserIdx()).orElseThrow();
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

    // 상품 경매 기능
    public BidDTO bidProxy(Long productId, BidRequestDTO dto, CustomUserDetail user) {
        Long userId = user.getUser().getUserIdx();
        ProductsEntity product = productsRepository.findById(productId).orElseThrow();

        // 동일한 아이디 요청
        if (product.getSellerIdx().getUserIdx().equals(user.getUser().getUserIdx())) {
            return BidDTO.builder()
                    .leaderName((product.getLeaderIdx() != null) ? product.getLeaderIdx().getName() : "없음")
                    .currentPrice(product.getCurrentPrice() != null ? product.getCurrentPrice() : null)
                    .message("본인이 올린 상품을 입찰할 수 없습니다.").build();
        }

        BidProxyEntity leader = bidProxyRepository.findFirstByProductId_ProductIdOrderByMaxPriceDesc(productId).orElse(null);
        BidProxyEntity proxy = bidProxyRepository.findByProductId_ProductIdAndUserId_UserIdx(productId, userId)
                .orElse(BidProxyEntity.builder()
                        .productId(product)
                        .userId(user.getUser())
                        .build());

        if (proxy.getMaxPrice() != null && dto.getMaxAmount() < proxy.getMaxPrice()) {
            return BidDTO.builder()
                    .currentPrice(product.getCurrentPrice() != null ? product.getCurrentPrice() : product.getStartPrice())
                    .leaderName(leader != null ? leader.getUserId().getName() : user.getUser().getName())
                    .message("현재 설정된 최대가보다 낮게 설정할 수 없습니다.")
                    .build();
        }
        proxy.setMaxPrice(dto.getMaxAmount());
        BidProxyEntity saved = bidProxyRepository.save(proxy);

        // 첫 입찰
        if (leader == null) {
            product.setCurrentPrice(product.getStartPrice());
            product.setLeaderIdx(saved.getUserId());
            productsRepository.save(product);
            return BidDTO.builder()
                    .leaderName(saved.getUserId().getName())
                    .currentPrice(product.getStartPrice())
                    .message("첫 입찰입니다.")
                    .build();
        }

        int leaderPrice = leader.getMaxPrice();
        int challengerPrice = saved.getMaxPrice();

        // 동일한 입찰자, 입찰금 수정
        if (leader.getUserId().getUserIdx().equals(user.getUser().getUserIdx())) {
            return BidDTO.builder()
                    .currentPrice(product.getCurrentPrice())
                    .leaderName(leader.getUserId().getName())
                    .message("최대 입찰가가 갱신되었습니다.")
                    .build();
        }

        // 도전자 금액이 최고 입찰자보다 큰 경우
        if (challengerPrice > leaderPrice) {
            product.setCurrentPrice(leaderPrice + product.getMinIncrement());
            product.setLeaderIdx(saved.getUserId());
            productsRepository.save(product);
            return BidDTO.builder()
                    .currentPrice(leaderPrice + product.getMinIncrement())
                    .leaderName(saved.getUserId().getName())
                    .message("입찰 경쟁에 승리하셨습니다. 최고 입찰자가 변경됩니다.")
                    .build();

            // 도전자 금액이 최고 입찰자보다 작은 경우
        } else if (challengerPrice < leaderPrice) {
            return BidDTO.builder()
                    .currentPrice(challengerPrice + product.getMinIncrement())
                    .leaderName(leader.getUserId().getName())
                    .message("입찰 경쟁에서 패배하셨습니다. 최고 입찰자가 유지됩니다.")
                    .build();

            // 도전자 금액과 최고 입찰자 금액이 같은 경우, 먼저 입찰한 사람이 승리
        } else {
            return BidDTO.builder()
                    .currentPrice(product.getCurrentPrice())
                    .leaderName(leader.getUserId().getName())
                    .message("최고 입찰자와 금액이 동일하여 최고 입찰자가 유지됩니다.")
                    .build();
        }
    }

    // 즉시 구매
    public BuyNowDTO buynow(Long productId, CustomUserDetail user) {
        ProductsEntity product = productsRepository.findById(productId).orElseThrow();
        if(product.getSellerIdx().equals(user.getUser())) {
            return BuyNowDTO.builder()
                    .message("본인이 올린 상품은 구매할 수 없습니다.")
                    .build();
        }
        product.setStatus(StatusEnum.ENDED);
        product.setLeaderIdx(user.getUser());
        product.setCurrentPrice(product.getBuyNow());
        product.setWinnerIdx(user.getUser());
        productsRepository.save(product);

        ChatRoomEntity room = ChatRoomEntity.builder()
                .buyerId(user.getUser())
                .sellarId(product.getSellerIdx())
                .productId(product)
                .build();
        ChatRoomEntity saved = chatRoomRepository.save(room);

        return BuyNowDTO.builder()
                .roomId(saved.getChatRoomId())
                .message("구매 성공하셨습니다.")
                .build();
    }

    // 상품 상태 관리 로직
    @Transactional
    @Scheduled(fixedRate = 60000)
    public void productScheduler() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        List<ProductsEntity> toStart = productsRepository.findByStatusAndStartAtBefore(StatusEnum.SCHEDULED, now);
        for (ProductsEntity product : toStart) {
            product.setStatus(StatusEnum.RUNNING);
        }

        List<ProductsEntity> toEnd = productsRepository.findByStatusAndEndAtBefore(StatusEnum.RUNNING, now);
        for (ProductsEntity product : toEnd) {
            product.setStatus(StatusEnum.ENDED);
            if (product.getLeaderIdx() != null) {
                product.setWinnerIdx(product.getLeaderIdx());
                UsersEntity buyer  = product.getLeaderIdx();
                UsersEntity seller = product.getSellerIdx();
                if (buyer == null || seller == null) return;

                // 이미 존재하면 생략
                if (chatRoomRepository.existsByBuyerIdAndSellarIdAndProductId(buyer, seller, product)) return;

                try {
                    chatRoomRepository.save(
                            ChatRoomEntity.builder()
                                    .buyerId(buyer)
                                    .sellarId(seller)
                                    .productId(product)
                                    .build()
                    );
                } catch (DataIntegrityViolationException e) {
                    // 유니크 제약 덕분에 경합 시 한 쪽만 성공 → 나머지는 여기로 들어오므로 무시
                    log.debug("Chat room already created concurrently. productId={}", product.getProductId());
                }
            }
        }
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
