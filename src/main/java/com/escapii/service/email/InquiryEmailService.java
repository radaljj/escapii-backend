package com.escapii.service.email;

import com.escapii.model.CustomDateInquiry;
import org.springframework.scheduling.annotation.Async;

public interface InquiryEmailService {

    @Async
    void sendTeamAlert(CustomDateInquiry inquiry);
}
