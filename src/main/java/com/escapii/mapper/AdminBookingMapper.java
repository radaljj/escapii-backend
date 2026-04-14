package com.escapii.mapper;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.model.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdminBookingMapper {

    @Mapping(source = "selectedDate.departureDate", target = "departureDate")
    @Mapping(source = "selectedDate.returnDate",    target = "returnDate")
    AdminBookingResponse toResponse(Booking entity);

    List<AdminBookingResponse> toResponseList(List<Booking> entities);
}
