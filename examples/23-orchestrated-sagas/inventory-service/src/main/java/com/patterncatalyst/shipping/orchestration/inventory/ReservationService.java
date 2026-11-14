package com.patterncatalyst.shipping.orchestration.inventory;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Reserve and release, both idempotent, and commutative with each other for one order. */
@ApplicationScoped
public class ReservationService {

    /** Takes the stock once per order. A repeat answers like the first call; a released order takes nothing. */
    @Transactional
    public Optional<Reply> reserve(ReserveStock command) {
        ReservationEntity existing = ReservationEntity.findById(command.orderId());
        if (existing != null) {
            return "RESERVED".equals(existing.status)
                    ? Optional.of(new Reply(command.orderId(), Reply.RESERVED))
                    : Optional.empty();
        }
        long updated = InventoryItemEntity.update(
                "quantityOnHand = quantityOnHand - ?1 where sku = ?2 and quantityOnHand >= ?1",
                command.quantity(), command.itemSku());
        if (updated == 0) {
            return Optional.of(new Reply(command.orderId(), Reply.INSUFFICIENT));
        }
        ReservationEntity.of(command.orderId(), command.itemSku(), command.quantity(), "RESERVED").persist();
        return Optional.of(new Reply(command.orderId(), Reply.RESERVED));
    }

    /** Puts the stock back once. With no reservation yet it records the release, so a late reserve is a no-op. */
    @Transactional
    public void release(String orderId) {
        ReservationEntity existing = ReservationEntity.findById(orderId);
        if (existing == null) {
            ReservationEntity.of(orderId, "-", 0, "RELEASED").persist();
        } else if ("RESERVED".equals(existing.status)) {
            InventoryItemEntity.update("quantityOnHand = quantityOnHand + ?1 where sku = ?2", existing.quantity, existing.sku);
            existing.status = "RELEASED";
        }
    }
}
