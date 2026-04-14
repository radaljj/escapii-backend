package com.escapii.mapper;

import com.escapii.dto.BookingResponse;
import com.escapii.model.Booking;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookingMapper {
    BookingResponse toResponse(Booking entity);
}
