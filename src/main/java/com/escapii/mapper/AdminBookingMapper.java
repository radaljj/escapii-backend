package com.escapii.mapper;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.PassengerDetail;
import com.escapii.model.Booking;
import com.escapii.model.PassengerInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class AdminBookingMapper {

    @Mapping(source = "selectedDate.departureDate",  target = "departureDate")
    @Mapping(source = "selectedDate.returnDate",     target = "returnDate")
    @Mapping(source = "exclusionCostEur",            target = "exclusionCostEur")
    @Mapping(source = "airlineName",                 target = "airlineName")
    @Mapping(source = "airlineBookingCode",          target = "airlineBookingCode")
    @Mapping(source = "hasRevealBox",                target = "hasRevealBox")
    @Mapping(source = "deliveryAddress",             target = "deliveryAddress")
    @Mapping(source = "deliveryCity",                target = "deliveryCity")
    @Mapping(source = "deliveryPhone",               target = "deliveryPhone")
    @Mapping(source = "revealBoxSent",               target = "revealBoxSent")
    @Mapping(target = "excludedDestinations", expression = "java(buildExclusionList(entity))")
    @Mapping(target = "passengers",           expression = "java(buildPassengers(entity))")
    public abstract AdminBookingResponse toResponse(Booking entity);

    public List<AdminBookingResponse> toResponseList(List<Booking> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    protected List<PassengerDetail> buildPassengers(Booking b) {
        return b.getPassengers().stream()
                .map(p -> PassengerDetail.builder()
                        .name(p.getName())
                        .gender(p.getGender())
                        .dateOfBirth(p.getDateOfBirth())
                        .passportNumber(p.getPassportNumber())
                        .hasValidPassport(p.getHasValidPassport())
                        .visaInfo(p.getVisaInfo())
                        .build())
                .toList();
    }

    protected List<String> buildExclusionList(Booking b) {
        List<String> list = new ArrayList<>();
        if (b.getExcludedDestination1() != null) list.add(b.getExcludedDestination1().getName());
        if (b.getExcludedDestination2() != null) list.add(b.getExcludedDestination2().getName());
        if (b.getExcludedDestination3() != null) list.add(b.getExcludedDestination3().getName());
        if (b.getExcludedDestination4() != null) list.add(b.getExcludedDestination4().getName());
        return list;
    }
}
