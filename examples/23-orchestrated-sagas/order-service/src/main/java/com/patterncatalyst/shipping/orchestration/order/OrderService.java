package com.patterncatalyst.shipping.orchestration.order;

import com.patterncatalyst.shipping.Order;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** The order table. Every state change is a conditional update, so a repeated callback changes nothing. */
@ApplicationScoped
public class OrderService {

    @Transactional
    public void place(Order order, String orchestrator) {
        OrderEntity row = new OrderEntity();
        row.id = order.id();
        row.customerId = order.customerId();
        row.itemSku = order.itemSku();
        row.quantity = order.quantity();
        row.amount = order.amount();
        row.status = "PLACED";
        row.orchestrator = orchestrator;
        row.persist();
    }

    @Transactional
    public OrderEntity find(String orderId) {
        return OrderEntity.findById(orderId);
    }

    @Transactional
    public OrderEntity findByInstance(String instanceId) {
        return OrderEntity.find("flowInstanceId", instanceId).firstResult();
    }

    @Transactional
    public void bindInstance(String orderId, String instanceId) {
        OrderEntity.update("flowInstanceId = ?1 where id = ?2", instanceId, orderId);
    }

    /** True when this call moved the order from PLACED to PAID. */
    @Transactional
    public boolean markPaid(String orderId) {
        return OrderEntity.update("status = 'PAID' where id = ?1 and status = 'PLACED'", orderId) == 1;
    }

    /** True when this call moved the order from PLACED to CANCELLED. */
    @Transactional
    public boolean cancel(String orderId) {
        return OrderEntity.update("status = 'CANCELLED' where id = ?1 and status = 'PLACED'", orderId) == 1;
    }

    @Transactional
    public ReserveStock reserveCommand(String orderId) {
        OrderEntity o = OrderEntity.findById(orderId);
        return new ReserveStock(o.id, o.itemSku, o.quantity);
    }

    @Transactional
    public TakePayment takeCommand(String orderId) {
        OrderEntity o = OrderEntity.findById(orderId);
        return new TakePayment(o.id, o.amount);
    }
}
