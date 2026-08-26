package com.escapii.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AgencyEarningsResponse {
    private final Long agencyId;
    private final String agencyName;
    private final int totalTerms;
    private final int totalTravelers;
    private final int totalRevenue;
    private final int totalCost;
    private final int totalProfit;
    private final int totalVoucher;
    private final List<TermEarning> terms;

    @Getter
    @Builder
    public static class TermEarning {
        private final Long dateId;
        private final String departureDate;
        private final String returnDate;
        private final String departureAirport;
        private final int travelers;
        private final int revenue;
        private final int cost;
        private final int profit;
        private final int voucher;
    }
}
