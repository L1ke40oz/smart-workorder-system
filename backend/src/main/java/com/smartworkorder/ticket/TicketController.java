package com.smartworkorder.ticket;

import com.smartworkorder.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class TicketController {
    private final TicketService tickets;
    private final CurrentUser current;
    public TicketController(TicketService tickets, CurrentUser current) { this.tickets = tickets; this.current = current; }
    @PostMapping("/tickets") @ResponseStatus(HttpStatus.CREATED)
    public TicketDtos.View create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TicketDtos.Create input) {
        return tickets.create(current.require(jwt), input);
    }
    @GetMapping("/tickets")
    public TicketDtos.Page list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) String status,
            @RequestParam(required=false) String category, @RequestParam(required=false) String query,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return tickets.list(current.require(jwt),status,category,query,page,size);
    }
    @GetMapping("/tickets/{id}")
    public TicketDtos.View detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return tickets.detail(current.require(jwt), id); }
    @PostMapping("/tickets/{id}/start")
    public TicketDtos.View start(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return tickets.start(current.admin(jwt),id); }
    @PostMapping("/tickets/{id}/complete")
    public TicketDtos.View complete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody TicketDtos.Complete input) {
        return tickets.complete(current.admin(jwt),id,input.resolution());
    }
    @GetMapping("/statistics")
    public TicketDtos.Statistics statistics(@AuthenticationPrincipal Jwt jwt) { return tickets.statistics(current.require(jwt)); }
}
