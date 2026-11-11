package com.patterncatalyst.shipping.orderservice;

/** The reference an event carries instead of the content: where to fetch it and how big it is, in bytes. */
public record Claim(String id, long size) {
}
