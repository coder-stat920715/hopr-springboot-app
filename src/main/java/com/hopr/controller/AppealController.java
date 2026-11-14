package com.hopr.controller;

import com.hopr.dto.AppealDtos.AppealConfigResponse;
import com.hopr.dto.AppealDtos.AppealForm;
import com.hopr.dto.AppealDtos.AppealRequest;
import com.hopr.dto.AppealDtos.AppealResponse;
import com.hopr.dto.AppealDtos.AppealStatusResponse;
import com.hopr.service.AppealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints: locked-out users have no JWT, so appeals are submitted and tracked anonymously. */
@RestController
@RequestMapping("/api/v1/appeals")
@RequiredArgsConstructor
public class AppealController {

    private final AppealService appealService;

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AppealResponse submitMultipart(@Valid @ModelAttribute AppealForm form) {
        return appealService.submit(form.toRequest(), form.getScreenshots());
    }

    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AppealResponse submitJson(@Valid @RequestBody AppealRequest req) {
        return appealService.submit(req, null);
    }

    @GetMapping("/status/{appealTicketNumber}")
    public AppealStatusResponse status(@PathVariable String appealTicketNumber) {
        return appealService.status(appealTicketNumber);
    }

    @GetMapping("/config")
    public AppealConfigResponse config() {
        return appealService.config();
    }
}
