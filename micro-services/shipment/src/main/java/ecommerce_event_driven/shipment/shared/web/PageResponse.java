package ecommerce_event_driven.shipment.shared.web;

import java.util.List;

public record PageResponse<T>(List<T> content, PageMeta page) {}
