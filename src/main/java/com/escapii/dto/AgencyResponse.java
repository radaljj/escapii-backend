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
    /** Pravni podaci za fakturu (opciono). */
    private String  legalName;
    private String  address;
    private String  pib;
    private String  mb;
}
