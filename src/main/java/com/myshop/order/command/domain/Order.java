package com.myshop.order.command.domain;

import com.myshop.common.event.Events;
import com.myshop.common.jpa.MoneyConverter;
import com.myshop.common.model.Money;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 도메인
 * - 핵심 규칙을 구현한 코드는 도메인 모델에만 위치하기 때문에 규칙이 바뀌거나 규칙을 확장해야 할 때 다른 코드에 영향을 덜 주고 변경 내역을 모델에 반경할 수 있다.
 */
@Entity
@Table(name = "purchase_order")
@Access(AccessType.FIELD)
public class Order {

    /**
     * 엔티티의 식별자
     * - OrderNo: 식별자를 위한 밸류 타입
     */
    @EmbeddedId
    private OrderNo number;

    @Version
    private long version;

    @Embedded
    private Orderer orderer;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_line", joinColumns = @JoinColumn(name = "order_number"))
    @OrderColumn(name = "line_idx")
    private List<OrderLine> orderLines;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "total_amounts")
    private Money totalAmounts;

    @Embedded
    private ShippingInfo shippingInfo;

    @Column(name = "state")
    @Enumerated(EnumType.STRING)
    private OrderState state;

    @Column(name = "order_date")
    private LocalDateTime orderDate;

    protected Order() {
    }

    /**
     * 도메인 객체가 불완전한 상태로 사용되는 것을 막기 위해 생성자를 통해 필요한 데이터를 모두 받자.
     */
    public Order(OrderNo number, Orderer orderer, List<OrderLine> orderLines,
                 ShippingInfo shippingInfo, OrderState state) {
        setNumber(number);
        setOrderer(orderer);
        setOrderLines(orderLines);
        setShippingInfo(shippingInfo);
        this.state = state;
        this.orderDate = LocalDateTime.now();
        Events.raise(new OrderPlacedEvent(number.getNumber(), orderer, orderLines, orderDate));
    }

    private void setNumber(OrderNo number) {
        if (number == null) throw new IllegalArgumentException("no number");
        this.number = number;
    }

    private void setOrderer(Orderer orderer) {
        if (orderer == null) throw new IllegalArgumentException("no orderer");
        this.orderer = orderer;
    }

    private void setOrderLines(List<OrderLine> orderLines) {
        verifyAtLeastOneOrMoreOrderLines(orderLines);
        this.orderLines = orderLines;
        calculateTotalAmounts();
    }

    /** --------------------------------------------------------------------------------
     * 도메인 모들 엔티티는 도메인 기능도 함께 제공
     */

    /**
     * 요구사항: 최소 한 종류 이상의 상품을 주문해야 한다.
     */
    private void verifyAtLeastOneOrMoreOrderLines(List<OrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            throw new IllegalArgumentException("no OrderLine");
        }
    }

    /**
     * 요구사항: 총 주문 금액은 각 상품의 구매 가격 합을 모두 더한 금액
     * - 루트 엔티티는 애그리거트 내부의 다른 객체(orderLines)를 조합해서 기능을 완성
     */
    private void calculateTotalAmounts() {
        this.totalAmounts = new Money(orderLines.stream()
                .mapToInt(x -> x.getAmounts().getValue()).sum());
    }

    // set 메서드의 접근 허용 범위는 private
    private void setShippingInfo(ShippingInfo shippingInfo) {
        if (shippingInfo == null) throw new IllegalArgumentException("no shipping info");
        // 밸류가 불변이면 새로운 객체를 할당해서 값을 변경
        this.shippingInfo = shippingInfo;
    }

    public OrderNo getNumber() {
        return number;
    }

    public long getVersion() {
        return version;
    }

    public Orderer getOrderer() {
        return orderer;
    }

    public Money getTotalAmounts() {
        return totalAmounts;
    }

    public ShippingInfo getShippingInfo() {
        return shippingInfo;
    }

    public OrderState getState() {
        return state;
    }

    /**
     * 요구사항: 출고를 하면 배송지를 변경할 수 없다.
     */
    public void changeShippingInfo(ShippingInfo newShippingInfo) {
        verifyNotYetShipped();
        setShippingInfo(newShippingInfo);
        Events.raise(new ShippingInfoChangedEvent(number, newShippingInfo));
    }

    /**
     * 요구사항: 출고 전에 주문을 취소할 수 있다.
     */
    public void cancel() {
        verifyNotYetShipped();
        this.state = OrderState.CANCELED;
        Events.raise(new OrderCanceledEvent(number.getNumber()));
    }

    // 출고 전 상태인지 확인
    private void verifyNotYetShipped() {
        if (!isNotYetShipped())
            throw new AlreadyShippedException();
    }

    /**
     * 요구사항: 고객이 결제를 완료하기 전에는 상품을 준비하지 않는다.
     */
    public boolean isNotYetShipped() {
        return state == OrderState.PAYMENT_WAITING || state == OrderState.PREPARING;
    }

    public List<OrderLine> getOrderLines() {
        return orderLines;
    }

    public boolean matchVersion(long version) {
        return this.version == version;
    }

    // 요구사항: 발송 시작
    public void startShipping() {
        verifyShippableState();
        this.state = OrderState.SHIPPED;
        Events.raise(new ShippingStartedEvent(number.getNumber()));
    }

    // 발송 가능한 상태인지 확인
    private void verifyShippableState() {
        verifyNotYetShipped();
        verifyNotCanceled();
    }

    // 취소되지 않은 상태인지 확인
    private void verifyNotCanceled() {
        if (state == OrderState.CANCELED) {
            throw new OrderAlreadyCanceledException();
        }
    }
}
