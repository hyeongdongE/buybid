package com.example.saleapp;

import com.example.saleapp.config.CustomUserDetail;
import com.example.saleapp.dto.BidRequestDTO;
import com.example.saleapp.entity.*;
import com.example.saleapp.repository.*;
import com.example.saleapp.service.ProductService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuctionIntegrationTest {
    @Autowired ProductService service;
    @Autowired ProductsRepository products;
    @Autowired UsersRepository users;
    @Autowired CategoriesRepository categories;
    @Autowired ChatRoomRepository rooms;
    @Autowired TransactionTemplate transaction;

    @Test
    void persistsThePriceAfterALowerProxyBidAndCapsItAtTheWinnerLimit() {
        var fixture = fixture();
        var first = service.bidProxy(fixture.productId(), new BidRequestDTO(2000), fixture.first());
        assertThat(first.getCurrentPrice()).isEqualTo(1000);
        var lower = service.bidProxy(fixture.productId(), new BidRequestDTO(1950), fixture.second());
        assertThat(lower.getCurrentPrice()).isEqualTo(2000);
        assertThat(products.findById(fixture.productId()).orElseThrow().getCurrentPrice()).isEqualTo(2000);
        var higher = service.bidProxy(fixture.productId(), new BidRequestDTO(2050), fixture.second());
        assertThat(higher.getCurrentPrice()).isEqualTo(2050);
        assertThat(products.findById(fixture.productId()).orElseThrow().getCurrentPrice()).isEqualTo(2050);
    }

    @Test
    void keepsFirstAcceptedBidderWhenLimitsAreEqual() {
        var fixture = fixture();
        service.bidProxy(fixture.productId(), new BidRequestDTO(2200), fixture.first());
        var result = service.bidProxy(fixture.productId(), new BidRequestDTO(2200), fixture.second());
        assertThat(result.getLeaderName()).isEqualTo(fixture.first().getUser().getName());
        assertThat(result.getCurrentPrice()).isEqualTo(2200);
    }

    @Test
    void rejectsBidsAfterDeadlineEvenWhenStatusStillShowsRunning() {
        var fixture = fixture();
        var product = products.findById(fixture.productId()).orElseThrow();
        product.setEndAt(now().minusSeconds(1));
        products.save(product);
        assertThatThrownBy(() -> service.bidProxy(fixture.productId(), new BidRequestDTO(2000), fixture.first()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMissingBuyNowPriceAndBelowOpeningBid() {
        var fixture = fixture();
        var product = products.findById(fixture.productId()).orElseThrow();
        product.setBuyNow(null);
        products.save(product);
        assertThatThrownBy(() -> service.buynow(fixture.productId(), fixture.first()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.bidProxy(fixture.productId(), new BidRequestDTO(999), fixture.first()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void raisingToAnExistingMaximumKeepsTheBidderWhoReachedItFirst() {
        var fixture = fixture();
        service.bidProxy(fixture.productId(), new BidRequestDTO(1100), fixture.first());
        service.bidProxy(fixture.productId(), new BidRequestDTO(2200), fixture.second());
        var matching = service.bidProxy(fixture.productId(), new BidRequestDTO(2200), fixture.first());
        assertThat(matching.getLeaderName()).isEqualTo(fixture.second().getUser().getName());
    }

    @Test
    void buyNowPriceIsNotAnAcceptedProxyLimit() {
        var fixture = fixture();
        assertThatThrownBy(() -> service.bidProxy(fixture.productId(), new BidRequestDTO(5000), fixture.first()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void closingRetainsOneRoomAndProcessesOtherDueAuctions() {
        var first = fixture();
        var second = fixture();
        service.bidProxy(first.productId(), new BidRequestDTO(2000), first.first());
        service.bidProxy(second.productId(), new BidRequestDTO(2100), second.first());
        for (long id : new long[]{first.productId(), second.productId()}) {
            var product = products.findById(id).orElseThrow();
            product.setEndAt(now().minusSeconds(1));
            products.save(product);
        }
        service.productScheduler();
        service.productScheduler();
        assertThat(rooms.findByProductId_ProductId(first.productId())).isPresent();
        assertThat(rooms.findByProductId_ProductId(second.productId())).isPresent();
        assertThat(products.findById(first.productId()).orElseThrow().getStatus()).isEqualTo(StatusEnum.ENDED);
        assertThat(products.findById(second.productId()).orElseThrow().getStatus()).isEqualTo(StatusEnum.ENDED);
    }

    @Test
    void rollingBackPurchaseLeavesAuctionOpenAndNoRoom() {
        var fixture = fixture();
        transaction.executeWithoutResult(status -> {
            assertThat(service.buynow(fixture.productId(), fixture.first()).getRoomId()).isNotNull();
            status.setRollbackOnly();
        });
        var product = products.findById(fixture.productId()).orElseThrow();
        assertThat(product.getStatus()).isEqualTo(StatusEnum.RUNNING);
        assertThat(product.getWinnerIdx()).isNull();
        assertThat(rooms.findByProductId_ProductId(fixture.productId())).isEmpty();
    }

    @RepeatedTest(10)
    void twoConcurrentPurchasesProduceOneWinnerAndOneRoom() throws Exception {
        var fixture = fixture();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> purchase(fixture.productId(), fixture.first(), ready, start));
            var second = executor.submit(() -> purchase(fixture.productId(), fixture.second(), ready, start));
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.stream.Stream.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull).toList()).hasSize(1);
        }
        var room = rooms.findByProductId_ProductId(fixture.productId()).orElseThrow();
        assertThat(products.findById(fixture.productId()).orElseThrow().getWinnerIdx().getUserIdx())
                .isEqualTo(room.getBuyerId().getUserIdx());
    }

    private Long purchase(long productId, CustomUserDetail buyer, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
        try {
            return service.buynow(productId, buyer).getRoomId();
        } catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode().value()).isEqualTo(409);
            return null;
        }
    }

    private Fixture fixture() {
        var seller = user();
        var product = products.save(ProductsEntity.builder()
                .title("auction").content("fixture").sellerIdx(seller)
                .cateId(categories.save(CategoriesEntity.builder().name(UUID.randomUUID().toString()).build()))
                .startAt(now().minusMinutes(5)).endAt(now().plusHours(1))
                .startPrice(1000).minIncrement(100).buyNow(5000).status(StatusEnum.RUNNING).build());
        return new Fixture(product.getProductId(), new CustomUserDetail(user()), new CustomUserDetail(user()));
    }

    private UsersEntity user() {
        var name = UUID.randomUUID().toString();
        return users.save(UsersEntity.builder().email(name + "@example.test").name(name)
                .password("unused").created_at(now()).role("ROLE_USER").build());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    private record Fixture(Long productId, CustomUserDetail first, CustomUserDetail second) {}
}
