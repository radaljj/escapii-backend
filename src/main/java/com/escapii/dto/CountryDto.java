package com.escapii.dto;

public class CountryDto {
    private String code;
    private String nameEn;
    private String nameSr;

    public CountryDto() {}

    public CountryDto(String code, String nameEn, String nameSr) {
        this.code = code;
        this.nameEn = nameEn;
        this.nameSr = nameSr;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getNameSr() { return nameSr; }
    public void setNameSr(String nameSr) { this.nameSr = nameSr; }
}
