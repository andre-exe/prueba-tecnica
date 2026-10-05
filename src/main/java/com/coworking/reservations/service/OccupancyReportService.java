package com.coworking.reservations.service;

import com.coworking.reservations.dto.response.OccupancyResponse;

import java.time.OffsetDateTime;
import java.util.List;

public interface OccupancyReportService {

    List<OccupancyResponse> occupancy(OffsetDateTime from, OffsetDateTime to);
}
