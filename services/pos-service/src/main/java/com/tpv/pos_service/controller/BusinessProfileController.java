package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.BusinessProfileResponse;
import com.tpv.pos_service.dto.UpdateBusinessProfileRequest;
import com.tpv.pos_service.service.BusinessProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/business-profile")
public class BusinessProfileController {

    private final BusinessProfileService service;

    public BusinessProfileController(BusinessProfileService service) {
        this.service = service;
    }

    @GetMapping
    public BusinessProfileResponse get() {
        return service.getProfile();
    }

    @PutMapping
    public BusinessProfileResponse update(@Valid @RequestBody UpdateBusinessProfileRequest request) {
        return service.updateProfile(request);
    }
}
