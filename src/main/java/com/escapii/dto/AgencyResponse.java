package com.escapii.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgencyResponse {
    private Long    id;
    private String  name;
    private String  contactName;
    private String  contactEmail;
    private String  contactPhone;
    private String  notes;
    private Boolean active;
}
