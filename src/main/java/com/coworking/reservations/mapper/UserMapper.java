package com.coworking.reservations.mapper;

import com.coworking.reservations.domain.entity.User;
import com.coworking.reservations.dto.response.UserResponse;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
