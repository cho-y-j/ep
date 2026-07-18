package com.skep.quotetemplate;

import com.skep.quotetemplate.dto.QuoteTemplateResponse;
import com.skep.quotetemplate.dto.SaveQuoteTemplateRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quote-templates")
@RequiredArgsConstructor
public class QuoteTemplateController {

    private final QuoteTemplateService service;

    @PostMapping
    public QuoteTemplateResponse create(@Valid @RequestBody SaveQuoteTemplateRequest req,
                                        @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @GetMapping
    public List<QuoteTemplateResponse> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor);
    }

    @GetMapping("/{id}")
    public QuoteTemplateResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    @PutMapping("/{id}")
    public QuoteTemplateResponse update(@PathVariable Long id, @Valid @RequestBody SaveQuoteTemplateRequest req,
                                        @CurrentUser AuthenticatedUser actor) {
        return service.update(id, req, actor);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.delete(id, actor);
    }
}
