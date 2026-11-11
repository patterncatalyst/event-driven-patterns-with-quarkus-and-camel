package com.patterncatalyst.shipping.notificationservice;

/** The reference to a stored attachment: its id on the order-service and its size in bytes. */
public record Claim(String id, long size) {
}
