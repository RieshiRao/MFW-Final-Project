package com.smartcity;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.*;
import org.springframework.http.*;
import jakarta.persistence.*;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class SmartCityWasteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCityWasteApplication.class, args);
    }

    // Load initial trucks on startup
    @Bean
    CommandLineRunner initData(TruckRepository truckRepo) {
        return args -> {
            truckRepo.saveAll(List.of(
                new Truck(null, "Truck A", "Zone 1"),
                new Truck(null, "Truck B", "Zone 2")
            ));
            System.out.println("🚛 Trucks initialized.");
        };
    }
}

@Entity
class Truck {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private String zone;

    public Truck() {}
    public Truck(Long id, String name, String zone) {
        this.id = id; this.name = name; this.zone = zone;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getZone() { return zone; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setZone(String zone) { this.zone = zone; }
}

@Entity
class CitizenRequest {
    @Id @GeneratedValue
    private Long id;
    private String citizenName;
    private String wasteType;
    private String zone;
    private String status;

    @ManyToOne
    private Truck assignedTruck;

    public CitizenRequest() {}
    public CitizenRequest(Long id, String citizenName, String wasteType, String zone, String status, Truck truck) {
        this.id = id;
        this.citizenName = citizenName;
        this.wasteType = wasteType;
        this.zone = zone;
        this.status = status;
        this.assignedTruck = truck;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getCitizenName() { return citizenName; }
    public String getWasteType() { return wasteType; }
    public String getZone() { return zone; }
    public String getStatus() { return status; }
    public Truck getAssignedTruck() { return assignedTruck; }
    public void setId(Long id) { this.id = id; }
    public void setCitizenName(String citizenName) { this.citizenName = citizenName; }
    public void setWasteType(String wasteType) { this.wasteType = wasteType; }
    public void setZone(String zone) { this.zone = zone; }
    public void setStatus(String status) { this.status = status; }
    public void setAssignedTruck(Truck assignedTruck) { this.assignedTruck = assignedTruck; }
}

interface TruckRepository extends JpaRepository<Truck, Long> {
    List<Truck> findByZone(String zone);
}

interface CitizenRequestRepository extends JpaRepository<CitizenRequest, Long> {
    List<CitizenRequest> findByAssignedTruckIsNull();
    List<CitizenRequest> findByZone(String zone);
}

@RestController
@RequestMapping("/api")
class WasteController {
    private final TruckRepository truckRepo;
    private final CitizenRequestRepository requestRepo;

    WasteController(TruckRepository truckRepo, CitizenRequestRepository requestRepo) {
        this.truckRepo = truckRepo;
        this.requestRepo = requestRepo;
    }

    // 🟢 Citizen: Create a pickup request
    @PostMapping("/requests")
    public ResponseEntity<CitizenRequest> createRequest(@RequestBody CitizenRequest req) {
        req.setStatus("PENDING");
        return ResponseEntity.ok(requestRepo.save(req));
    }

    // 🟢 Citizen: View all requests
    @GetMapping("/requests")
    public List<CitizenRequest> getRequests() {
        return requestRepo.findAll();
    }

    // 🟢 Admin: Assign request to a truck manually
    @PostMapping("/assign/{requestId}/to/{truckId}")
    public ResponseEntity<?> assignRequest(@PathVariable Long requestId, @PathVariable Long truckId) {
        CitizenRequest req = requestRepo.findById(requestId).orElseThrow();
        Truck truck = truckRepo.findById(truckId).orElseThrow();
        req.setAssignedTruck(truck);
        req.setStatus("ASSIGNED");
        return ResponseEntity.ok(requestRepo.save(req));
    }

    // 🧠 Admin: Auto-assign unassigned requests
    @PostMapping("/assign/auto")
    public ResponseEntity<?> autoAssign() {
        List<CitizenRequest> unassigned = requestRepo.findByAssignedTruckIsNull();
        int assignedCount = 0;

        for (CitizenRequest req : unassigned) {
            List<Truck> trucks = truckRepo.findByZone(req.getZone());
            if (!trucks.isEmpty()) {
                req.setAssignedTruck(trucks.get(0));
                req.setStatus("ASSIGNED");
                requestRepo.save(req);
                assignedCount++;
            }
        }
        return ResponseEntity.ok("✅ Auto-assigned " + assignedCount + " requests.");
    }

    // 📊 Admin: Get pending requests by zone
    @GetMapping("/stats/pending-by-zone")
    public Map<String, Long> pendingByZone() {
        return requestRepo.findAll().stream()
            .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
            .collect(Collectors.groupingBy(CitizenRequest::getZone, Collectors.counting()));
    }
}
