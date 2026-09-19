package ecommerce_event_driven.user.shared.web;

import java.util.List;

public record PageResponse<T>(List<T> content, PageMeta page) {}
