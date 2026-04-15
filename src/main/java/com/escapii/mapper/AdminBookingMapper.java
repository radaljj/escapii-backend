package com.escapii.mapper;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.model.Booking;
import com.escapii.model.Destination;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class AdminBookingMapper {

    @Mapping(source = "selectedDate.departureDate",  target = "departureDate")
    @Mapping(source = "selectedDate.returnDate",     target = "returnDate")
    @Mapping(source = "exclusionCostEur",            target = "exclusionCostEur")
    @Mapping(target = "excludedDestinations", expression = "java(buildExclusionList(entity))")
    @Mapping(target = "passengerNames",       expression = "java(buildPassengerNames(entity))")
    public abstract AdminBookingResponse toResponse(Booking entity);

    public List<AdminBookingResponse> toResponseList(List<Booking> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    protected List<String> buildPassengerNames(Booking b) {
        return b.getPassengers().stream()
                .map(p -> p.getName())
                .toList();
    }

    protected List<String> buildExclusionList(Booking b) {
        List<String> list = new ArrayList<>();
        if (b.getExcludedDestination1() != null) list.add(b.getExcludedDestination1().getName());
        if (b.getExcludedDestination2() != null) list.add(b.getExcludedDestination2().getName());
        if (b.getExcludedDestination3() != null) list.add(b.getExcludedDestination3().getName());
        if (b.getExcludedDestination4() != null) list.add(b.getExcludedDestination4().getName());
        if (b.getExcludedDestination5() != null) list.add(b.getExcludedDestination5().getName());
        return list;
    }
}
