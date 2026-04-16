package com.escapii.mapper;

import com.escapii.dto.DateResponse;
import com.escapii.model.AvailableDate;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = DestinationMapper.class)
public interface DateMapper {
    DateResponse toResponse(AvailableDate entity);
    List<DateResponse> toResponseList(List<AvailableDate> entities);
}
