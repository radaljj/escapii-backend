package com.escapii.mapper;

import com.escapii.dto.DestinationResponse;
import com.escapii.model.Destination;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DestinationMapper {
    DestinationResponse toResponse(Destination entity);
    List<DestinationResponse> toResponseList(List<Destination> entities);
}
