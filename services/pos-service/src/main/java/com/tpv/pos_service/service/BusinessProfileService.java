package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.BusinessProfile;
import com.tpv.pos_service.dto.BusinessProfileResponse;
import com.tpv.pos_service.dto.UpdateBusinessProfileRequest;
import com.tpv.pos_service.repository.BusinessProfileRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessProfileService {

    private final BusinessProfileRepository repository;

    public BusinessProfileService(BusinessProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse getProfile() {
        return toResponse(getOrCreateProfile());
    }

    @Transactional
    public BusinessProfileResponse updateProfile(UpdateBusinessProfileRequest request) {
        BusinessProfile profile = getOrCreateProfile();
        profile.apply(
                normalizeRequired(request.businessName(), "businessName"),
                normalizeOptional(request.legalName()),
                normalizeOptional(request.taxId()).toUpperCase(Locale.ROOT),
                normalizeOptional(request.address()),
                normalizeOptional(request.postalCode()),
                normalizeOptional(request.city()),
                normalizeOptional(request.province()),
                normalizeCountry(request.country()),
                normalizeOptional(request.phone()),
                normalizeOptional(request.email()).toLowerCase(Locale.ROOT)
        );
        return toResponse(profile);
    }

    private BusinessProfile getOrCreateProfile() {
        return repository.findById(BusinessProfile.SINGLETON_ID)
                .orElseGet(() -> repository.save(new BusinessProfile(BusinessProfile.SINGLETON_ID, "Restaurante EL GUSTO")));
    }

    private static BusinessProfileResponse toResponse(BusinessProfile profile) {
        return new BusinessProfileResponse(
                profile.getId(),
                profile.getBusinessName(),
                profile.getLegalName(),
                profile.getTaxId(),
                profile.getAddress(),
                profile.getPostalCode(),
                profile.getCity(),
                profile.getProvince(),
                profile.getCountry(),
                profile.getPhone(),
                profile.getEmail(),
                profile.getUpdatedAt()
        );
    }

    private static String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeCountry(String value) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            return "ES";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }
}
