package com.scantaker.api.controller;

import com.scantaker.api.common.ApiException;
import com.scantaker.api.dto.CentreDtos.*;
import com.scantaker.api.entity.Centre;
import com.scantaker.api.entity.CentrePrice;
import com.scantaker.api.entity.Scan;
import com.scantaker.api.repository.CentrePriceRepository;
import com.scantaker.api.repository.CentreRepository;
import com.scantaker.api.repository.ScanRepository;
import com.scantaker.api.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/centres")
@RequiredArgsConstructor
public class AdminCentreController {
    private final CentreRepository centres;
    private final ScanRepository scans;
    private final CentrePriceRepository centrePrices;
    private final NotificationService notifications;

    public record CentreScanPriceView(
        Long scanId,
        String scanSlug,
        String scanName,
        String scanType,
        String bodyPart,
        int defaultPrice,
        int centrePrice,
        boolean available,
        String commissionType,
        Double commissionValue
    ) {}

    public record UpdatePriceItem(
        Long scanId,
        int price,
        Boolean available,
        String commissionType,
        Double commissionValue
    ) {}

    @GetMapping
    public List<View> all() { return centres.findAll().stream().map(View::from).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Request r) {
        Centre c = new Centre();
        c.setVerified(true);
        Centre saved = centres.save(apply(c, r));
        notifications.notifyAdmin("New Centre Added", saved.getName() + " was added to the network.", "/admin/centres");
        return View.from(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public View update(@PathVariable Long id, @Valid @RequestBody Request r) {
        Centre c = centres.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Centre not found"));
        apply(c, r);
        Centre saved = centres.save(c);

        if (r.commissionValue() != null || r.commissionType() != null) {
            List<CentrePrice> cpList = centrePrices.findByCentreId(id);
            for (CentrePrice cp : cpList) {
                if (r.commissionType() != null) cp.setCommissionType(r.commissionType());
                if (r.commissionValue() != null) cp.setCommissionValue(r.commissionValue());
                cp.setUpdatedAt(Instant.now());
            }
            centrePrices.saveAll(cpList);
        }

        return View.from(saved);
    }

    @PutMapping("/{id}/verify")
    public View verify(@PathVariable Long id) {
        Centre c = centres.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Centre not found"));
        c.setVerified(true);
        c.setActive(true);
        Centre saved = centres.save(c);
        notifications.notifyCentre(id, "Account Approved! 🎉", "Your diagnostic centre has been approved by admin! You can now manage patient bookings.", "/centre/dashboard");
        notifications.notifyAdmin("Centre Approved", saved.getName() + " has been verified & activated.", "/admin/centres");
        return View.from(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        try {
            centrePrices.deleteByCentreId(id);
            centres.deleteById(id);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.CONFLICT, "Centre is linked with bookings or accounts. Please mark it inactive instead.");
        }
    }

    @GetMapping("/{id}/prices")
    public List<CentreScanPriceView> getPrices(@PathVariable Long id) {
        Centre c = centres.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Centre not found"));
        List<Scan> allScans = scans.findAllByOrderBySortOrderAsc();
        List<CentrePrice> cpList = centrePrices.findByCentreId(id);
        Map<Long, CentrePrice> cpMap = new HashMap<>();
        for (CentrePrice cp : cpList) {
            cpMap.put(cp.getScan().getId(), cp);
        }

        List<CentreScanPriceView> result = new ArrayList<>();
        for (Scan s : allScans) {
            CentrePrice cp = cpMap.get(s.getId());
            int cPrice = cp != null ? cp.getPrice() : s.getPrice();
            boolean avail = cp == null || cp.isAvailable();
            String cType = c.getCommissionType() != null ? c.getCommissionType() : (cp != null && cp.getCommissionType() != null ? cp.getCommissionType() : "PERCENT");
            Double cVal = c.getCommissionValue() != null ? c.getCommissionValue() : (cp != null && cp.getCommissionValue() != null ? cp.getCommissionValue() : 15.0);
            result.add(new CentreScanPriceView(
                s.getId(),
                s.getSlug(),
                s.getName(),
                s.getType().name(),
                s.getBodyPart(),
                s.getPrice(),
                cPrice,
                avail,
                cType != null ? cType : "PERCENT",
                cVal != null ? cVal : 15.0
            ));
        }
        return result;
    }

    @PutMapping("/{id}/prices")
    @Transactional
    public List<CentreScanPriceView> updatePrices(@PathVariable Long id, @RequestBody List<UpdatePriceItem> items) {
        Centre c = centres.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Centre not found"));
        for (UpdatePriceItem item : items) {
            if (item.scanId() == null) continue;
            Scan s = scans.findById(item.scanId()).orElse(null);
            if (s == null) continue;

            CentrePrice cp = centrePrices.findByCentreIdAndScanId(id, item.scanId())
                .orElseGet(() -> {
                    CentrePrice n = new CentrePrice();
                    n.setCentre(c);
                    n.setScan(s);
                    return n;
                });
            cp.setPrice(item.price() > 0 ? item.price() : s.getPrice());
            if (item.available() != null) cp.setAvailable(item.available());
            if (item.commissionType() != null) cp.setCommissionType(item.commissionType());
            if (item.commissionValue() != null) cp.setCommissionValue(item.commissionValue());
            cp.setUpdatedAt(Instant.now());
            centrePrices.save(cp);
        }
        notifications.notifyCentre(id, "Pricing & Commission Updated", "Scan prices and commission rates for your centre have been updated by admin.", "/centre/dashboard");
        return getPrices(id);
    }

    @PutMapping("/{id}/slots")
    public View updateSlots(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Centre c = centres.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Centre not found"));
        String slotsJson = body.get("slotsJson");
        if (slotsJson != null) {
            c.setAvailableSlotsJson(slotsJson);
            centres.save(c);
        }
        return View.from(c);
    }

    private Centre apply(Centre c, Request r) {
        c.setName(r.name().trim());
        c.setArea(r.area().trim());
        c.setAddress(r.address());
        c.setPhone(r.phone());
        if (r.email() != null) c.setEmail(r.email());
        if (r.contactPerson() != null) c.setContactPerson(r.contactPerson().trim());
        if (r.modalities() != null) c.setModalities(r.modalities());
        if (r.defaultDiscount() != null) c.setDefaultDiscount(r.defaultDiscount());
        if (r.commissionType() != null) c.setCommissionType(r.commissionType());
        if (r.commissionValue() != null) c.setCommissionValue(r.commissionValue());
        if (r.availableSlotsJson() != null) c.setAvailableSlotsJson(r.availableSlotsJson());
        if (r.active() != null) c.setActive(r.active());
        if (r.verified() != null) c.setVerified(r.verified());
        if (r.imageUrl() != null) c.setImageUrl(r.imageUrl());
        if (r.description() != null) c.setDescription(r.description());
        if (r.timings() != null) c.setTimings(r.timings());
        if (r.facilities() != null) c.setFacilities(r.facilities());
        return c;
    }
}