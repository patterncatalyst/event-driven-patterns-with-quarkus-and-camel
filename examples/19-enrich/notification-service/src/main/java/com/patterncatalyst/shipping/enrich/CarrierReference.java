package com.patterncatalyst.shipping.enrich;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patterncatalyst.shipping.Carrier;

/**
 * What shipping-service knows about a carrier: how it moves a parcel and where its tracking page lives. The template
 * holds the placeholder {@code {trackingNumber}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CarrierReference(Carrier carrier, String mode, String trackingUrlTemplate) {
}
