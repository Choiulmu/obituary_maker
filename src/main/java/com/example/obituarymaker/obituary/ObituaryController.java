package com.example.obituarymaker.obituary;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ObituaryController {

    private static final Logger log = LoggerFactory.getLogger(ObituaryController.class);

    private final ObituaryService obituaryService;

    public ObituaryController(ObituaryService obituaryService) {
        this.obituaryService = obituaryService;
    }

    @InitBinder
    public void trimInput(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/obituaries/new")
    public String newForm(Model model) {
        model.addAttribute("obituary", new Obituary());
        return "obituary/form";
    }

    @PostMapping("/obituaries/preview")
    public String preview(@Valid @ModelAttribute Obituary obituary, BindingResult bindingResult) {
        return bindingResult.hasErrors() ? "obituary/form" : "obituary/preview";
    }

    @PostMapping("/obituaries")
    public String create(@Valid @ModelAttribute Obituary obituary, BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "obituary/form";
        }
        try {
            String id = obituaryService.create(obituary);
            redirectAttributes.addFlashAttribute("name", obituary.getName());
            return "redirect:/obituaries/" + id + "/complete";
        } catch (Exception e) {
            log.error("부고장 생성 실패", e);
            bindingResult.reject("createFailed", "부고장을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return "obituary/form";
        }
    }

    @GetMapping("/obituaries/{id}/complete")
    public String complete(@PathVariable String id, Model model) {
        model.addAttribute("shareUrl", obituaryService.shareUrl(id));
        return "obituary/complete";
    }
}
