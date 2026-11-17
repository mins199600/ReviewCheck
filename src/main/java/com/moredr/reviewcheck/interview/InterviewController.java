package com.moredr.reviewcheck.interview;

import com.moredr.reviewcheck.github.RepoFetchException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/interview")
    public String start(@RequestParam("repoUrl") String repoUrl, Model model) {
        try {
            InterviewSession session = interviewService.start(repoUrl);
            return "redirect:/interview/" + session.getId();
        } catch (RepoFetchException | InterviewException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("repoUrl", repoUrl);
            return "index";
        }
    }

    @GetMapping("/interview/{id}")
    public String view(@PathVariable String id, Model model, RedirectAttributes redirect) {
        return interviewService.find(id)
                .map(session -> {
                    model.addAttribute("interview", session);
                    return "interview";
                })
                .orElseGet(() -> {
                    redirect.addFlashAttribute("error", "면접을 찾을 수 없어요. 서버를 다시 켜면 기록이 사라져요.");
                    return "redirect:/";
                });
    }

    @PostMapping("/interview/{id}/answer")
    public String answer(@PathVariable String id, @RequestParam("answer") String answer,
                         RedirectAttributes redirect) {
        try {
            interviewService.answer(id, answer);
        } catch (InterviewException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("draft", answer);
        }
        return "redirect:/interview/" + id;
    }
}
