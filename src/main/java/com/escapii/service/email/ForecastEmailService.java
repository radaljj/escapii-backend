package com.escapii.service.email;

import com.escapii.model.Booking;
import com.escapii.service.weather.DailyForecast;

import java.util.List;

public interface ForecastEmailService {
    void sendForecastEmail(Booking booking, List<DailyForecast> forecast);
}
