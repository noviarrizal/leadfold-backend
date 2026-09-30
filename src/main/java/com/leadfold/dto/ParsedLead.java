package com.leadfold.dto;

// Result of running a raw message through LeadParserService (Claude).
// extractedFieldsJson is kept as a raw JSON string since its shape is
// intentionally open-ended (budget, preferred_date, service_type, ...) —
// whatever the model finds worth surfacing for this particular message.
public record ParsedLead(
        String name,
        String contact,
        String interestSummary,
        String extractedFieldsJson
) {}
